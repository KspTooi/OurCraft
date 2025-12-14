package com.ksptool.ourcraft.clientj.world.mesh;

import com.ksptool.ourcraft.clientj.world.ClientWorld;
import com.ksptool.ourcraft.clientj.world.chunk.FlexClientChunk;
import com.ksptool.ourcraft.sharedcore.GlobalPalette;
import com.ksptool.ourcraft.sharedcore.blocks.inner.SharedBlock;
import com.ksptool.ourcraft.sharedcore.enums.BlockEnums;
import com.ksptool.ourcraft.sharedcore.utils.FlexChunkData;
import com.ksptool.ourcraft.sharedcore.world.BlockState;
import java.util.ArrayList;
import java.util.List;

/**
 * 客户端Flex区块网格异步生成器类，使用线程池异步生成区块网格数据
 * 适配FlexChunkData架构，使用快照机制实现线程安全的无锁读取
 */
public class FlexChunkMeshGenerator {

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

    public FlexChunkMeshGenerator(ClientWorld clientWorld) {
    }

    public MeshGenerationResult calculateMeshData(FlexClientChunk chunk, ClientWorld clientWorld) {
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
        int chunkSizeX = chunk.getSizeX();
        int chunkSizeZ = chunk.getSizeZ();

        for (int x = 0; x < chunkSizeX; x++) {
            for (int y = 0; y < chunkHeight; y++) {
                for (int z = 0; z < chunkSizeZ; z++) {
                    // 使用快照无锁读取方块状态
                    BlockState state = snapshot.getBlock(x, y, z);
                    SharedBlock sharedBlock = state.getSharedBlock();
                    
                    // 使用isAir方法进行快速判断（避免对象创建）
                    if (snapshot.isAir(x, y, z)) {
                        continue;
                    }

                    int worldX = chunkX * chunkSizeX + x;
                    int worldZ = chunkZ * chunkSizeZ + z;

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

        return new MeshGenerationResult(chunk.getChunkX(), chunk.getChunkZ(), verticesArray, texCoordsArray, tintsArray, animationDataArray, indicesArray,
                transparentVerticesArray, transparentTexCoordsArray, transparentTintsArray, transparentAnimationDataArray, transparentIndicesArray);
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

    private boolean shouldRenderFace(ClientWorld clientWorld, int x, int y, int z, int dx, int dy, int dz,
                                     SharedBlock currentSharedBlock, FlexClientChunk currentChunk, FlexChunkData.Snapshot currentSnapshot) {
        int neighborX = x + dx;
        int neighborY = y + dy;
        int neighborZ = z + dz;
        
        // 检查Y轴边界 (使用快照中的高度)
        if (neighborY < 0 || neighborY >= currentSnapshot.getHeight()) {
            return true;
        }
        
        // 计算邻居方块所在的区块坐标
        int chunkSizeX = currentChunk.getSizeX();
        int chunkSizeZ = currentChunk.getSizeZ();
        int neighborChunkX = (int) Math.floor((float) neighborX / chunkSizeX);
        int neighborChunkZ = (int) Math.floor((float) neighborZ / chunkSizeZ);
        
        BlockState neighborState;
        
        // 如果邻居在本区块内，使用快照无锁读取
        if (neighborChunkX == currentChunk.getChunkX() && neighborChunkZ == currentChunk.getChunkZ()) {
            int localX = neighborX - neighborChunkX * chunkSizeX;
            int localZ = neighborZ - neighborChunkZ * chunkSizeZ;
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
        float[] tex = getTextureCoords(state, face.textureFace);
        float u0 = tex[0], v0 = tex[1], u1 = tex[2], v1 = tex[3];
        
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

        float[] greenColor = getGreenColor();
        for (int i = 0; i < 4; i++) {
            tints.add(greenColor[0]);
            tints.add(greenColor[1]);
            tints.add(greenColor[2]);
            tints.add(greenColor[3]);
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

    private float[] getTextureCoords(BlockState state, int face) {
        return new float[]{0.0f, 0.0f, 1.0f, 1.0f};
    }
    
    private float[] getAnimationData(BlockState state, int face) {
        return new float[]{0.0f, 0.0f, 0.0f};
    }

    private float[] getGreenColor() {
        return new float[]{0.2f, 0.8f, 0.2f, 1.0f};
    }
}
