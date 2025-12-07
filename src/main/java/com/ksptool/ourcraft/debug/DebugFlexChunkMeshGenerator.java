package com.ksptool.ourcraft.debug;

import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import com.ksptool.ourcraft.sharedcore.GlobalPalette;
import com.ksptool.ourcraft.sharedcore.blocks.inner.SharedBlock;
import com.ksptool.ourcraft.sharedcore.enums.BlockEnums;
import com.ksptool.ourcraft.sharedcore.utils.FlexChunkData;
import com.ksptool.ourcraft.sharedcore.world.BlockState;
import lombok.extern.slf4j.Slf4j;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.List;
import java.util.concurrent.*;

/**
 * JME客户端Flex区块网格异步生成器类，使用线程池异步生成区块网格数据
 * 适配FlexChunkData架构，使用快照机制实现线程安全的无锁读取
 * Debug版本：不使用纹理，使用硬编码颜色
 */
@Slf4j
public class DebugFlexChunkMeshGenerator {

    private enum BlockFace {
        TOP(0, 1, 0, 0, new int[][]{{0, 1, 0}, {1, 1, 0}, {1, 1, 1}, {0, 1, 1}}, new int[]{0, 3, 2, 0, 2, 1}, new int[][]{{0, 1}, {1, 1}, {1, 0}, {0, 0}}),
        BOTTOM(0, -1, 0, 1, new int[][]{{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}}, new int[]{0, 1, 2, 0, 2, 3}, new int[][]{{0, 0}, {1, 0}, {1, 1}, {0, 1}}),
        NORTH(0, 0, -1, 2, new int[][]{{0, 0, 0}, {1, 0, 0}, {1, 1, 0}, {0, 1, 0}}, new int[]{0, 3, 2, 0, 2, 1}, new int[][]{{0, 1}, {1, 1}, {1, 0}, {0, 0}}),
        SOUTH(0, 0, 1, 2, new int[][]{{1, 0, 1}, {0, 0, 1}, {0, 1, 1}, {1, 1, 1}}, new int[]{0, 3, 2, 0, 2, 1}, new int[][]{{0, 1}, {1, 1}, {1, 0}, {0, 0}}),
        WEST(-1, 0, 0, 2, new int[][]{{0, 0, 1}, {0, 0, 0}, {0, 1, 0}, {0, 1, 1}}, new int[]{0, 3, 2, 0, 2, 1}, new int[][]{{0, 1}, {1, 1}, {1, 0}, {0, 0}}),
        EAST(1, 0, 0, 2, new int[][]{{1, 0, 0}, {1, 0, 1}, {1, 1, 1}, {1, 1, 0}}, new int[]{0, 3, 2, 0, 2, 1}, new int[][]{{0, 1}, {1, 1}, {1, 0}, {0, 0}});

        public final int dx;
        public final int dy;
        public final int dz;
        public final int textureFace;
        public final int[][] vertexOffsets;
        public final int[] indices;
        public final int[][] texCoordOrder;

        BlockFace(int dx, int dy, int dz, int textureFace, int[][] vertexOffsets, int[] indices, int[][] texCoordOrder) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.textureFace = textureFace;
            this.vertexOffsets = vertexOffsets;
            this.indices = indices;
            this.texCoordOrder = texCoordOrder;
        }
    }

    private final ExecutorService executor;
    private final List<Future<DebugMeshGenerationResult>> pendingFutures = new CopyOnWriteArrayList<>();
    private final BlockStateProvider blockStateProvider;

    @FunctionalInterface
    public interface BlockStateProvider {
        int getBlockState(int x, int y, int z);
    }

    public DebugFlexChunkMeshGenerator(BlockStateProvider blockStateProvider) {
        this.blockStateProvider = blockStateProvider;
        int numThreads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        this.executor = Executors.newFixedThreadPool(numThreads);
    }

    public void submitMeshTask(DebugFlexClientChunk chunk) {
        Callable<DebugMeshGenerationResult> task = () -> calculateMeshData(chunk, blockStateProvider);
        Future<DebugMeshGenerationResult> future = executor.submit(task);
        pendingFutures.add(future);
    }

    public List<Future<DebugMeshGenerationResult>> getPendingFutures() {
        return pendingFutures;
    }

    public void shutdown() {
        executor.shutdown();
    }

    // 简单的原生数组列表，避免自动装箱/拆箱带来的巨大GC压力
    private static class FloatList {
        public float[] elements;
        public int size = 0;

        public FloatList(int initialCapacity) {
            elements = new float[initialCapacity];
        }

        public void add(float value) {
            if (size == elements.length) {
                float[] newElements = new float[elements.length * 2];
                System.arraycopy(elements, 0, newElements, 0, size);
                elements = newElements;
            }
            elements[size++] = value;
        }

        public float[] toArray() {
            float[] result = new float[size];
            System.arraycopy(elements, 0, result, 0, size);
            return result;
        }
        
        public void clear() {
            size = 0;
        }
    }

    private static class IntList {
        public int[] elements;
        public int size = 0;

        public IntList(int initialCapacity) {
            elements = new int[initialCapacity];
        }

        public void add(int value) {
            if (size == elements.length) {
                int[] newElements = new int[elements.length * 2];
                System.arraycopy(elements, 0, newElements, 0, size);
                elements = newElements;
            }
            elements[size++] = value;
        }

        public int[] toArray() {
            int[] result = new int[size];
            System.arraycopy(elements, 0, result, 0, size);
            return result;
        }
        
        public void clear() {
            size = 0;
        }
    }

    public DebugMeshGenerationResult calculateMeshData(DebugFlexClientChunk chunk, BlockStateProvider blockStateProvider) {
        // 预估容量：16x16x16 chunk ~ 4096 blocks. 假设 10% 是表面。 ~400 blocks. 
        // 400 * 6 faces * 4 verts * 3 floats = ~28800 floats.
        // 给个保守的初始值，减少扩容次数
        int initialCapacity = 4096;
        
        FloatList vertices = new FloatList(initialCapacity);
        FloatList texCoords = new FloatList(initialCapacity);
        FloatList tints = new FloatList(initialCapacity);
        FloatList animationData = new FloatList(initialCapacity);
        IntList indices = new IntList(initialCapacity);

        FloatList transparentVertices = new FloatList(initialCapacity / 2);
        FloatList transparentTexCoords = new FloatList(initialCapacity / 2);
        FloatList transparentTints = new FloatList(initialCapacity / 2);
        FloatList transparentAnimationData = new FloatList(initialCapacity / 2);
        IntList transparentIndices = new IntList(initialCapacity / 2);

        int vertexOffset = 0;
        int transparentVertexOffset = 0;

        // 创建快照用于无锁读取
        FlexChunkData.Snapshot snapshot = chunk.createSnapshot();
        int chunkX = chunk.getChunkX();
        int chunkZ = chunk.getChunkZ();
        int chunkHeight = snapshot.getHeight();

        for (int x = 0; x < DebugFlexClientChunk.CHUNK_SIZE; x++) {
            for (int y = 0; y < chunkHeight; y++) {
                for (int z = 0; z < DebugFlexClientChunk.CHUNK_SIZE; z++) {
                    // 使用快照无锁读取方块状态
                    BlockState state = snapshot.getBlock(x, y, z);
                    
                    // 使用isAir方法进行快速判断（避免对象创建）
                    if (snapshot.isAir(x, y, z)) {
                        continue;
                    }

                    SharedBlock sharedBlock = state.getSharedBlock();
                    int worldX = chunkX * DebugFlexClientChunk.CHUNK_SIZE + x;
                    int worldZ = chunkZ * DebugFlexClientChunk.CHUNK_SIZE + z;

                    boolean isFluid = sharedBlock.isFluid();
                    FloatList targetVertices = isFluid ? transparentVertices : vertices;
                    FloatList targetTexCoords = isFluid ? transparentTexCoords : texCoords;
                    FloatList targetTints = isFluid ? transparentTints : tints;
                    FloatList targetAnimationData = isFluid ? transparentAnimationData : animationData;
                    IntList targetIndices = isFluid ? transparentIndices : indices;
                    int currentOffset = isFluid ? transparentVertexOffset : vertexOffset;
                    
                    // 暂存 offset 变化量，避免每次循环都更新局部变量导致逻辑复杂
                    // int addedFaces = 0;

                    for (BlockFace face : BlockFace.values()) {
                        if (shouldRenderFace(blockStateProvider, worldX, y, worldZ, face.dx, face.dy, face.dz, sharedBlock, chunk, snapshot)) {
                            // 使用局部坐标 x, y, z 生成网格，因为 Geometry 会设置平移
                            addFace(face, targetVertices, targetTexCoords, targetTints, targetAnimationData, targetIndices, x, y, z, state, currentOffset);
                            currentOffset += 4;
                            // addedFaces++;
                        }
                    }

                    if (isFluid) {
                        transparentVertexOffset = currentOffset;
                    } else {
                        vertexOffset = currentOffset;
                    }
                }
            }
        }

        return new DebugMeshGenerationResult(chunk.getChunkX(), chunk.getChunkZ(), 
                vertices.toArray(), texCoords.toArray(), tints.toArray(), animationData.toArray(), indices.toArray(),
                transparentVertices.toArray(), transparentTexCoords.toArray(), transparentTints.toArray(), transparentAnimationData.toArray(), transparentIndices.toArray());
    }

    /**
     * 将网格生成结果应用到JME Geometry
     */
    public void applyMeshResult(DebugFlexClientChunk chunk, DebugMeshGenerationResult result, com.jme3.scene.Node rootNode, com.jme3.asset.AssetManager assetManager) {
        // 移除旧的Geometry（如果存在）
        if (chunk.hasGeometry()) {
            chunk.getGeometry().removeFromParent();
        }
        if (chunk.hasTransparentGeometry()) {
            chunk.getTransparentGeometry().removeFromParent();
        }
        
        if (result.vertices.length > 0) {
            Mesh mesh = createJmeMesh(result.vertices, result.texCoords, result.tints, result.indices);
            Geometry geometry = new Geometry("Chunk_" + result.chunkX + "_" + result.chunkZ, mesh);
            geometry.setLocalTranslation(result.chunkX * DebugFlexClientChunk.CHUNK_SIZE, 0, result.chunkZ * DebugFlexClientChunk.CHUNK_SIZE);
            
            // 设置材质
            Material material = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
            material.setBoolean("VertexColor", true); // 开启顶点颜色
            geometry.setMaterial(material);
            
            chunk.setGeometry(geometry);
            rootNode.attachChild(geometry);
        }
        
        if (result.transparentVertices.length > 0) {
            Mesh transparentMesh = createJmeMesh(result.transparentVertices, result.transparentTexCoords, result.transparentTints, result.transparentIndices);
            Geometry transparentGeometry = new Geometry("Chunk_Transparent_" + result.chunkX + "_" + result.chunkZ, transparentMesh);
            transparentGeometry.setLocalTranslation(result.chunkX * DebugFlexClientChunk.CHUNK_SIZE, 0, result.chunkZ * DebugFlexClientChunk.CHUNK_SIZE);
            
            // 设置透明材质
            Material transparentMaterial = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
            transparentMaterial.setBoolean("VertexColor", true); // 开启顶点颜色
            transparentMaterial.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
            transparentGeometry.setMaterial(transparentMaterial);
            
            chunk.setTransparentGeometry(transparentGeometry);
            rootNode.attachChild(transparentGeometry);
        }
    }

    private Mesh createJmeMesh(float[] vertices, float[] texCoords, float[] colors, int[] indices) {
        Mesh mesh = new Mesh();
        
        // 设置顶点位置
        FloatBuffer vertexBuffer = BufferUtils.createFloatBuffer(vertices);
        mesh.setBuffer(VertexBuffer.Type.Position, 3, vertexBuffer);
        
        // 设置纹理坐标 (虽然不需要，但保留以防 shader 需要)
        if (texCoords.length > 0) {
            FloatBuffer texCoordBuffer = BufferUtils.createFloatBuffer(texCoords);
            mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, texCoordBuffer);
        }
        
        // 设置顶点颜色
        if (colors.length > 0) {
            FloatBuffer colorBuffer = BufferUtils.createFloatBuffer(colors);
            mesh.setBuffer(VertexBuffer.Type.Color, 4, colorBuffer);
        }
        
        // 设置索引
        IntBuffer indexBuffer = BufferUtils.createIntBuffer(indices);
        mesh.setBuffer(VertexBuffer.Type.Index, 3, indexBuffer);
        
        mesh.updateBound();
        return mesh;
    }

    // 移除了 convertToFloatArray 和 convertToIntArray 方法，因为不再需要

    private boolean shouldRenderFace(BlockStateProvider blockStateProvider, int x, int y, int z, int dx, int dy, int dz,
                                     SharedBlock currentSharedBlock, DebugFlexClientChunk currentChunk, FlexChunkData.Snapshot currentSnapshot) {
        int neighborX = x + dx;
        int neighborY = y + dy;
        int neighborZ = z + dz;
        
        // 检查Y轴边界 (使用快照中的高度)
        if (neighborY < 0 || neighborY >= currentSnapshot.getHeight()) {
            return true;
        }
        
        // 计算邻居方块所在的区块坐标
        int neighborChunkX = (int) Math.floor((float) neighborX / DebugFlexClientChunk.CHUNK_SIZE);
        int neighborChunkZ = (int) Math.floor((float) neighborZ / DebugFlexClientChunk.CHUNK_SIZE);
        
        BlockState neighborState;
        
        // 如果邻居在本区块内，使用快照无锁读取
        if (neighborChunkX == currentChunk.getChunkX() && neighborChunkZ == currentChunk.getChunkZ()) {
            int localX = neighborX - neighborChunkX * DebugFlexClientChunk.CHUNK_SIZE;
            int localZ = neighborZ - neighborChunkZ * DebugFlexClientChunk.CHUNK_SIZE;
            neighborState = currentSnapshot.getBlock(localX, neighborY, localZ);
        } else {
            // 跨区块访问，通过BlockStateProvider访问
            int neighborStateId = blockStateProvider.getBlockState(neighborX, neighborY, neighborZ);
            GlobalPalette palette = GlobalPalette.getInstance();
            neighborState = palette.getState(neighborStateId);
        }
        
        SharedBlock neighborSharedBlock = neighborState.getSharedBlock();
        
        // 快速判断空气
        if (neighborSharedBlock.getStdRegName().equals(BlockEnums.AIR.getStdRegName())) {
            return true;
        }
        
        boolean neighborIsSolid = neighborSharedBlock.isSolid();
        boolean neighborIsFluid = neighborSharedBlock.isFluid();
        
        if (!neighborIsSolid && !neighborIsFluid) {
            return true;
        }
        
        boolean currentIsSolid = currentSharedBlock.isSolid();
        boolean currentIsFluid = currentSharedBlock.isFluid();
        
        if (currentIsSolid && neighborIsFluid) {
            return true;
        }
        
        if (currentIsFluid && neighborIsSolid) {
            return true;
        }
        
        return false;
    }

    private void addFace(BlockFace face, FloatList vertices, FloatList texCoords, FloatList tints, FloatList animationData, IntList indices, int x, int y, int z, BlockState state, int offset) {
        // 不再需要纹理名称和UV坐标
        // SharedBlock sharedBlock = state.getSharedBlock();
        // String textureName = sharedBlock.getTextureName(world, face.textureFace, state);
        
        float u0 = 0.0f, v0 = 0.0f, u1 = 1.0f, v1 = 1.0f;
        
        // 移除 JmeTextureManager 相关逻辑
        
        for (int i = 0; i < 4; i++) {
            int[] vOffset = face.vertexOffsets[i];
            vertices.add((float) (x + vOffset[0]));
            vertices.add((float) (y + vOffset[1]));
            vertices.add((float) (z + vOffset[2]));
        }

        for (int i = 0; i < 4; i++) {
            int[] texOrder = face.texCoordOrder[i];
            float u = texOrder[0] == 0 ? u0 : u1;
            float v = texOrder[1] == 0 ? v0 : v1;
            texCoords.add(u);
            texCoords.add(v);
        }

        float[] color = getBlockColor(state, y);
        for (int i = 0; i < 4; i++) {
            tints.add(color[0]);
            tints.add(color[1]);
            tints.add(color[2]);
            tints.add(color[3]);
        }

        // 动画数据填充 0
        for (int i = 0; i < 4; i++) {
            animationData.add(0.0f);
            animationData.add(0.0f);
            animationData.add(0.0f);
        }

        for (int i = 0; i < face.indices.length; i++) {
            indices.add(offset + face.indices[i]);
        }
    }

    private float[] getBlockColor(BlockState state, int y) {
        SharedBlock sharedBlock = state.getSharedBlock();
        String name = sharedBlock.getStdRegName().toString();
        
        float[] baseColor;
        
        if (name.equals(BlockEnums.GRASS_BLOCK.getStdRegName().toString())) {
            baseColor = new float[]{0.1f, 0.8f, 0.1f, 1.0f}; // Green
        } else if (name.equals(BlockEnums.DIRT.getStdRegName().toString())) {
            baseColor = new float[]{0.6f, 0.4f, 0.2f, 1.0f}; // Brown
        } else if (name.equals(BlockEnums.STONE.getStdRegName().toString())) {
            baseColor = new float[]{0.6f, 0.6f, 0.6f, 1.0f}; // Gray
        } else if (name.equals(BlockEnums.WOOD.getStdRegName().toString())) {
            baseColor = new float[]{0.5f, 0.3f, 0.1f, 1.0f}; // Wood
        } else if (name.equals(BlockEnums.LEAVES.getStdRegName().toString())) {
            baseColor = new float[]{0.0f, 0.5f, 0.0f, 1.0f}; // Dark Green
        } else if (name.equals(BlockEnums.WATER.getStdRegName().toString())) {
            baseColor = new float[]{0.2f, 0.2f, 0.9f, 0.6f}; // Blue Translucent
        } else {
            baseColor = new float[]{1.0f, 0.0f, 1.0f, 1.0f}; // Missing Purple
        }
        
        // 基于高度的视觉效果 (等高线/层级染色)
        // 奇数层变暗 15%，形成明显的层级感
        float brightness = (y % 2 == 0) ? 1.0f : 0.85f;
        
        // 额外的每8层加深，模拟粗略的等高线
        if (y % 8 == 0) {
            brightness *= 0.8f;
        }
        
        return new float[]{
            baseColor[0] * brightness, 
            baseColor[1] * brightness, 
            baseColor[2] * brightness, 
            baseColor[3]
        };
    }

    // 移除了 getAnimationData 和 getTintValue 方法，因为逻辑已经合并到 addFace 和 getBlockColor 中

}
