package com.ksptool.ourcraft.clientjme.world;

import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.texture.Texture;
import com.jme3.util.BufferUtils;
import com.ksptool.ourcraft.clientjme.rendering.JmeTextureManager;
import com.ksptool.ourcraft.sharedcore.GlobalPalette;
import com.ksptool.ourcraft.sharedcore.blocks.inner.SharedBlock;
import com.ksptool.ourcraft.sharedcore.enums.BlockEnums;
import com.ksptool.ourcraft.sharedcore.utils.FlexChunkData;
import com.ksptool.ourcraft.sharedcore.world.BlockState;
import lombok.extern.slf4j.Slf4j;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * JME客户端Flex区块网格异步生成器类，使用线程池异步生成区块网格数据
 * 适配FlexChunkData架构，使用快照机制实现线程安全的无锁读取
 */
@Slf4j
public class JmeFlexChunkMeshGenerator {

    private final ExecutorService executor;
    private final List<Future<JmeMeshGenerationResult>> pendingFutures = new CopyOnWriteArrayList<>();
    private final JmeClientWorld clientWorld;

    public JmeFlexChunkMeshGenerator(JmeClientWorld clientWorld) {
        this.clientWorld = clientWorld;
        int numThreads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        this.executor = Executors.newFixedThreadPool(numThreads);
    }

    public void submitMeshTask(JmeFlexClientChunk chunk) {
        Callable<JmeMeshGenerationResult> task = () -> calculateMeshData(chunk, clientWorld);
        Future<JmeMeshGenerationResult> future = executor.submit(task);
        pendingFutures.add(future);
    }

    public List<Future<JmeMeshGenerationResult>> getPendingFutures() {
        return pendingFutures;
    }

    public void shutdown() {
        executor.shutdown();
    }

    public JmeMeshGenerationResult calculateMeshData(JmeFlexClientChunk chunk, JmeClientWorld clientWorld) {
        List<Float> vertices = new ArrayList<>();
        List<Float> texCoords = new ArrayList<>();
        List<Float> tints = new ArrayList<>();
        List<Float> animationData = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        List<Float> transparentVertices = new ArrayList<>();
        List<Float> transparentTexCoords = new ArrayList<>();
        List<Float> transparentTints = new ArrayList<>();
        List<Float> transparentAnimationData = new ArrayList<>();
        List<Integer> transparentIndices = new ArrayList<>();

        int vertexOffset = 0;
        int transparentVertexOffset = 0;

        // 创建快照用于无锁读取
        FlexChunkData.Snapshot snapshot = chunk.createSnapshot();
        int chunkX = chunk.getChunkX();
        int chunkZ = chunk.getChunkZ();
        int chunkHeight = snapshot.getHeight();

        for (int x = 0; x < JmeFlexClientChunk.CHUNK_SIZE; x++) {
            for (int y = 0; y < chunkHeight; y++) {
                for (int z = 0; z < JmeFlexClientChunk.CHUNK_SIZE; z++) {
                    // 使用快照无锁读取方块状态
                    BlockState state = snapshot.getBlock(x, y, z);
                    SharedBlock sharedBlock = state.getSharedBlock();

                    // 使用isAir方法进行快速判断（避免对象创建）
                    if (snapshot.isAir(x, y, z)) {
                        continue;
                    }

                    int worldX = chunkX * JmeFlexClientChunk.CHUNK_SIZE + x;
                    int worldZ = chunkZ * JmeFlexClientChunk.CHUNK_SIZE + z;

                    boolean isFluid = sharedBlock.isFluid();
                    List<Float> targetVertices = isFluid ? transparentVertices : vertices;
                    List<Float> targetTexCoords = isFluid ? transparentTexCoords : texCoords;
                    List<Float> targetTints = isFluid ? transparentTints : tints;
                    List<Float> targetAnimationData = isFluid ? transparentAnimationData : animationData;
                    List<Integer> targetIndices = isFluid ? transparentIndices : indices;
                    int currentOffset = isFluid ? transparentVertexOffset : vertexOffset;

                    for (BlockFace face : BlockFace.values()) {
                        if (shouldRenderFace(clientWorld, worldX, y, worldZ, face.dx, face.dy, face.dz, sharedBlock, chunk, snapshot)) {
                            addFace(face, targetVertices, targetTexCoords, targetTints, targetAnimationData, targetIndices, worldX, y, worldZ, state, currentOffset);
                            currentOffset += 4;
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

        float[] verticesArray = convertToFloatArray(vertices);
        float[] texCoordsArray = convertToFloatArray(texCoords);
        float[] tintsArray = convertToFloatArray(tints);
        float[] animationDataArray = convertToFloatArray(animationData);
        int[] indicesArray = convertToIntArray(indices);

        float[] transparentVerticesArray = convertToFloatArray(transparentVertices);
        float[] transparentTexCoordsArray = convertToFloatArray(transparentTexCoords);
        float[] transparentTintsArray = convertToFloatArray(transparentTints);
        float[] transparentAnimationDataArray = convertToFloatArray(transparentAnimationData);
        int[] transparentIndicesArray = convertToIntArray(transparentIndices);

        return new JmeMeshGenerationResult(chunk.getChunkX(), chunk.getChunkZ(), verticesArray, texCoordsArray, tintsArray, animationDataArray, indicesArray,
                transparentVerticesArray, transparentTexCoordsArray, transparentTintsArray, transparentAnimationDataArray, transparentIndicesArray);
    }

    /**
     * 将网格生成结果应用到JME Geometry
     */
    public void applyMeshResult(JmeFlexClientChunk chunk, JmeMeshGenerationResult result, com.jme3.scene.Node rootNode, com.jme3.asset.AssetManager assetManager) {
        JmeTextureManager textureManager = JmeTextureManager.getInstance();
        Texture atlasTexture = textureManager.getTexture();

        if (result.vertices.length > 0) {
            Mesh mesh = createJmeMesh(result.vertices, result.texCoords, result.indices);
            Geometry geometry = new Geometry("Chunk_" + result.chunkX + "_" + result.chunkZ, mesh);
            geometry.setLocalTranslation(result.chunkX * JmeFlexClientChunk.CHUNK_SIZE, 0, result.chunkZ * JmeFlexClientChunk.CHUNK_SIZE);

            // 设置材质和纹理图集
            Material material = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
            if (atlasTexture != null) {
                material.setTexture("ColorMap", atlasTexture);
            } else {
                material.setColor("Color", ColorRGBA.White);
            }
            geometry.setMaterial(material);

            chunk.setGeometry(geometry);
            rootNode.attachChild(geometry);
        }

        if (result.transparentVertices.length > 0) {
            Mesh transparentMesh = createJmeMesh(result.transparentVertices, result.transparentTexCoords, result.transparentIndices);
            Geometry transparentGeometry = new Geometry("Chunk_Transparent_" + result.chunkX + "_" + result.chunkZ, transparentMesh);
            transparentGeometry.setLocalTranslation(result.chunkX * JmeFlexClientChunk.CHUNK_SIZE, 0, result.chunkZ * JmeFlexClientChunk.CHUNK_SIZE);

            // 设置透明材质和纹理图集
            Material transparentMaterial = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
            if (atlasTexture != null) {
                transparentMaterial.setTexture("ColorMap", atlasTexture);
            } else {
                transparentMaterial.setColor("Color", ColorRGBA.White);
            }
            transparentMaterial.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
            transparentGeometry.setMaterial(transparentMaterial);

            chunk.setTransparentGeometry(transparentGeometry);
            rootNode.attachChild(transparentGeometry);
        }
    }

    private Mesh createJmeMesh(float[] vertices, float[] texCoords, int[] indices) {
        Mesh mesh = new Mesh();

        // 设置顶点位置
        FloatBuffer vertexBuffer = BufferUtils.createFloatBuffer(vertices);
        mesh.setBuffer(VertexBuffer.Type.Position, 3, vertexBuffer);

        // 设置纹理坐标
        if (texCoords.length > 0) {
            FloatBuffer texCoordBuffer = BufferUtils.createFloatBuffer(texCoords);
            mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, texCoordBuffer);
        }

        // 设置索引
        IntBuffer indexBuffer = BufferUtils.createIntBuffer(indices);
        mesh.setBuffer(VertexBuffer.Type.Index, 3, indexBuffer);

        mesh.updateBound();
        return mesh;
    }

    private float[] convertToFloatArray(List<Float> list) {
        float[] array = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i);
        }
        return array;
    }

    private int[] convertToIntArray(List<Integer> list) {
        int[] array = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i);
        }
        return array;
    }

    private boolean shouldRenderFace(JmeClientWorld clientWorld, int x, int y, int z, int dx, int dy, int dz,
                                     SharedBlock currentSharedBlock, JmeFlexClientChunk currentChunk, FlexChunkData.Snapshot currentSnapshot) {
        int neighborX = x + dx;
        int neighborY = y + dy;
        int neighborZ = z + dz;

        // 检查Y轴边界 (使用快照中的高度)
        if (neighborY < 0 || neighborY >= currentSnapshot.getHeight()) {
            return true;
        }

        // 计算邻居方块所在的区块坐标
        int neighborChunkX = (int) Math.floor((float) neighborX / JmeFlexClientChunk.CHUNK_SIZE);
        int neighborChunkZ = (int) Math.floor((float) neighborZ / JmeFlexClientChunk.CHUNK_SIZE);

        BlockState neighborState;

        // 如果邻居在本区块内，使用快照无锁读取
        if (neighborChunkX == currentChunk.getChunkX() && neighborChunkZ == currentChunk.getChunkZ()) {
            int localX = neighborX - neighborChunkX * JmeFlexClientChunk.CHUNK_SIZE;
            int localZ = neighborZ - neighborChunkZ * JmeFlexClientChunk.CHUNK_SIZE;
            neighborState = currentSnapshot.getBlock(localX, neighborY, localZ);
        } else {
            // 跨区块访问，需要通过World安全访问（带锁）
            int neighborStateId = clientWorld.getBlockState(neighborX, neighborY, neighborZ);
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

    private void addFace(BlockFace face, List<Float> vertices, List<Float> texCoords, List<Float> tints, List<Float> animationData, List<Integer> indices, int x, int y, int z, BlockState state, int offset) {
        // 获取纹理名称和UV坐标
        SharedBlock sharedBlock = state.getSharedBlock();
        String textureName = sharedBlock.getTextureName(clientWorld, face.textureFace, state);

        float u0 = 0.0f, v0 = 0.0f, u1 = 1.0f, v1 = 1.0f;

        if (textureName != null) {
            JmeTextureManager textureManager = JmeTextureManager.getInstance();
            JmeTextureManager.UVCoords uvCoords = textureManager.getUVCoords(textureName);
            if (uvCoords != null) {
                u0 = uvCoords.u0;
                v0 = uvCoords.v0;
                u1 = uvCoords.u1;
                v1 = uvCoords.v1;
            }
        }

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

        float tintValue = getTintValue(state, face.textureFace);
        for (int i = 0; i < 4; i++) {
            tints.add(tintValue);
        }

        float[] animData = getAnimationData(state, face.textureFace);
        for (int i = 0; i < 4; i++) {
            animationData.add(animData[0]);
            animationData.add(animData[1]);
            animationData.add(animData[2]);
        }

        for (int i = 0; i < face.indices.length; i++) {
            indices.add(offset + face.indices[i]);
        }
    }

    private float[] getAnimationData(BlockState state, int face) {
        SharedBlock sharedBlock = state.getSharedBlock();
        String textureName = sharedBlock.getTextureName(clientWorld, face, state);
        if (textureName == null) {
            return new float[]{0.0f, 0.0f, 0.0f};
        }

        JmeTextureManager textureManager = JmeTextureManager.getInstance();
        JmeTextureManager.UVCoords uvCoords = textureManager.getUVCoords(textureName);

        if (uvCoords == null || !uvCoords.isAnimated) {
            return new float[]{0.0f, 0.0f, 0.0f};
        }

        return new float[]{(float) uvCoords.frameCount, uvCoords.frameTime, uvCoords.v0};
    }

    private float getTintValue(BlockState state, int face) {
        SharedBlock sharedBlock = state.getSharedBlock();

        if (sharedBlock.getStdRegName().equals(BlockEnums.GRASS_BLOCK.getStdRegName()) && face == 0) {
            return 1.0f;
        }

        if (sharedBlock.getStdRegName().equals(BlockEnums.LEAVES.getStdRegName())) {
            return 1.0f;
        }

        return 0.0f;
    }

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
}
