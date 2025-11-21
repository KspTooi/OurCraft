package com.ksptool.ourcraft.client.world;

import com.ksptool.ourcraft.client.rendering.TextureManager;
import com.ksptool.ourcraft.sharedcore.BlockType;
import com.ksptool.ourcraft.sharedcore.blocks.inner.SharedBlock;
import com.ksptool.ourcraft.sharedcore.world.BlockState;
import com.ksptool.ourcraft.sharedcore.world.GlobalPalette;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * 客户端区块网格异步生成器类，使用线程池异步生成区块网格数据
 */
public class ChunkMeshGenerator {

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
    private final List<Future<MeshGenerationResult>> pendingFutures = new CopyOnWriteArrayList<>();
    private final ClientWorld clientWorld;

    public ChunkMeshGenerator(ClientWorld clientWorld) {
        this.clientWorld = clientWorld;
        int numThreads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        this.executor = Executors.newFixedThreadPool(numThreads);
    }

    public void submitMeshTask(ClientChunk chunk) {
        Callable<MeshGenerationResult> task = () -> calculateMeshData(chunk, clientWorld);
        Future<MeshGenerationResult> future = executor.submit(task);
        pendingFutures.add(future);
    }

    public List<Future<MeshGenerationResult>> getPendingFutures() {
        return pendingFutures;
    }

    public void shutdown() {
        executor.shutdown();
    }

    public MeshGenerationResult calculateMeshData(ClientChunk chunk, ClientWorld clientWorld) {
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

        GlobalPalette palette = GlobalPalette.getInstance();
        int[][][] blockStates = chunk.getBlockStates();
        int chunkX = chunk.getChunkX();
        int chunkZ = chunk.getChunkZ();
        int AIR_STATE_ID = 0;

        for (int x = 0; x < ClientChunk.CHUNK_SIZE; x++) {
            for (int y = 0; y < ClientChunk.CHUNK_HEIGHT; y++) {
                for (int z = 0; z < ClientChunk.CHUNK_SIZE; z++) {
                    int stateId = blockStates[x][y][z];
                    BlockState state = palette.getState(stateId);
                    SharedBlock sharedBlock = state.getSharedBlock();
                    
                    if (stateId == AIR_STATE_ID) {
                        continue;
                    }

                    int worldX = chunkX * ClientChunk.CHUNK_SIZE + x;
                    int worldZ = chunkZ * ClientChunk.CHUNK_SIZE + z;

                    boolean isFluid = sharedBlock.isFluid();
                    List<Float> targetVertices = isFluid ? transparentVertices : vertices;
                    List<Float> targetTexCoords = isFluid ? transparentTexCoords : texCoords;
                    List<Float> targetTints = isFluid ? transparentTints : tints;
                    List<Float> targetAnimationData = isFluid ? transparentAnimationData : animationData;
                    List<Integer> targetIndices = isFluid ? transparentIndices : indices;
                    int currentOffset = isFluid ? transparentVertexOffset : vertexOffset;

                    for (BlockFace face : BlockFace.values()) {
                        if (shouldRenderFace(clientWorld, worldX, y, worldZ, face.dx, face.dy, face.dz, sharedBlock)) {
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

    private boolean shouldRenderFace(ClientWorld clientWorld, int x, int y, int z, int dx, int dy, int dz, SharedBlock currentSharedBlock) {
        int neighborStateId = clientWorld.getBlockState(x + dx, y + dy, z + dz);
        GlobalPalette palette = GlobalPalette.getInstance();
        BlockState neighborState = palette.getState(neighborStateId);
        SharedBlock neighborSharedBlock = neighborState.getSharedBlock();
        
        int AIR_STATE_ID = 0;
        if (neighborStateId == AIR_STATE_ID) {
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

    private float[] getTextureCoords(BlockState state, int face) {
        SharedBlock sharedBlock = state.getSharedBlock();
        String textureName = sharedBlock.getTextureName(clientWorld, face, state);
        if (textureName == null) {
            return new float[]{0.0f, 0.0f, 1.0f, 1.0f};
        }

        TextureManager textureManager = TextureManager.getInstance();
        TextureManager.UVCoords uvCoords = textureManager.getUVCoords(textureName);
        
        if (uvCoords == null) {
            return new float[]{0.0f, 0.0f, 1.0f, 1.0f};
        }
        
        return new float[]{uvCoords.u0, uvCoords.v0, uvCoords.u1, uvCoords.v1};
    }
    
    private float[] getAnimationData(BlockState state, int face) {
        SharedBlock sharedBlock = state.getSharedBlock();
        String textureName = sharedBlock.getTextureName(clientWorld, face, state);
        if (textureName == null) {
            return new float[]{0.0f, 0.0f, 0.0f};
        }
        
        TextureManager textureManager = TextureManager.getInstance();
        TextureManager.UVCoords uvCoords = textureManager.getUVCoords(textureName);
        
        if (uvCoords == null || !uvCoords.isAnimated) {
            return new float[]{0.0f, 0.0f, 0.0f};
        }
        
        return new float[]{(float) uvCoords.frameCount, uvCoords.frameTime, uvCoords.v0};
    }

    private float getTintValue(BlockState state, int face) {
        SharedBlock sharedBlock = state.getSharedBlock();
        String textureName = sharedBlock.getTextureName(clientWorld, face, state);
        if (textureName == null) {
            return 0.0f;
        }
        
        if (sharedBlock.getStdRegName().equals(BlockType.GRASS_BLOCK.getStdRegName()) && face == 0) {
            return 1.0f;
        }
        
        if (sharedBlock.getStdRegName().equals(BlockType.LEAVES.getStdRegName())) {
            return 1.0f;
        }
        
        return 0.0f;
    }
}
