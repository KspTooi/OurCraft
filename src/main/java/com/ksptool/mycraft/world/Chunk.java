package com.ksptool.mycraft.world;

import com.ksptool.mycraft.entity.BoundingBox;
import com.ksptool.mycraft.rendering.Mesh;
import com.ksptool.mycraft.rendering.TextureManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 区块类，存储方块数据并生成渲染网格
 */
public class Chunk {

    //区块大小
    public static final int CHUNK_SIZE = 16;
    
    //区块高度
    public static final int CHUNK_HEIGHT = 256;

    public enum ChunkState {
        NEW,
        DATA_LOADED,
        AWAITING_MESH,
        READY_TO_UPLOAD,
        READY
    }

    private int[][][] blockStates;
    private int chunkX;
    private int chunkZ;
    private Mesh mesh;
    private Mesh transparentMesh;
    private boolean needsUpdate;
    private ChunkState state;
    private BoundingBox boundingBox;
    private static final int AIR_STATE_ID = 0;
    private boolean isDirty = false;
    private boolean entitiesDirty = false;

    public Chunk(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.blockStates = new int[CHUNK_SIZE][CHUNK_HEIGHT][CHUNK_SIZE];
        this.needsUpdate = true;
        this.state = ChunkState.NEW;
        
        float minX = chunkX * CHUNK_SIZE;
        float maxX = minX + CHUNK_SIZE;
        float minZ = chunkZ * CHUNK_SIZE;
        float maxZ = minZ + CHUNK_SIZE;
        this.boundingBox = new BoundingBox(minX, 0, minZ, maxX, CHUNK_HEIGHT, maxZ);
    }

    public void setBlockState(int x, int y, int z, int stateId) {
        if (x >= 0 && x < CHUNK_SIZE && y >= 0 && y < CHUNK_HEIGHT && z >= 0 && z < CHUNK_SIZE) {
            blockStates[x][y][z] = stateId;
            needsUpdate = true;
            markDirty(true);
        }
    }

    public int getBlockState(int x, int y, int z) {
        if (x >= 0 && x < CHUNK_SIZE && y >= 0 && y < CHUNK_HEIGHT && z >= 0 && z < CHUNK_SIZE) {
            return blockStates[x][y][z];
        }
        return AIR_STATE_ID;
    }

    public MeshGenerationResult calculateMeshData(World world) {
        if (!needsUpdate) {
            return null;
        }

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

        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int y = 0; y < CHUNK_HEIGHT; y++) {
                for (int z = 0; z < CHUNK_SIZE; z++) {
                    int stateId = blockStates[x][y][z];
                    BlockState state = palette.getState(stateId);
                    Block block = state.getBlock();
                    
                    if (stateId == AIR_STATE_ID) {
                        continue;
                    }

                    int worldX = chunkX * CHUNK_SIZE + x;
                    int worldZ = chunkZ * CHUNK_SIZE + z;

                    if (block.isFluid()) {
                        if (shouldRenderFace(world, worldX, y, worldZ, 0, -1, 0, block)) {
                            addBottomFace(transparentVertices, transparentTexCoords, transparentTints, transparentAnimationData, transparentIndices, worldX, y, worldZ, state, transparentVertexOffset);
                            transparentVertexOffset += 4;
                        }
                        if (shouldRenderFace(world, worldX, y, worldZ, 0, 1, 0, block)) {
                            addTopFace(transparentVertices, transparentTexCoords, transparentTints, transparentAnimationData, transparentIndices, worldX, y, worldZ, state, transparentVertexOffset);
                            transparentVertexOffset += 4;
                        }
                        if (shouldRenderFace(world, worldX, y, worldZ, -1, 0, 0, block)) {
                            addWestFace(transparentVertices, transparentTexCoords, transparentTints, transparentAnimationData, transparentIndices, worldX, y, worldZ, state, transparentVertexOffset);
                            transparentVertexOffset += 4;
                        }
                        if (shouldRenderFace(world, worldX, y, worldZ, 1, 0, 0, block)) {
                            addEastFace(transparentVertices, transparentTexCoords, transparentTints, transparentAnimationData, transparentIndices, worldX, y, worldZ, state, transparentVertexOffset);
                            transparentVertexOffset += 4;
                        }
                        if (shouldRenderFace(world, worldX, y, worldZ, 0, 0, -1, block)) {
                            addNorthFace(transparentVertices, transparentTexCoords, transparentTints, transparentAnimationData, transparentIndices, worldX, y, worldZ, state, transparentVertexOffset);
                            transparentVertexOffset += 4;
                        }
                        if (shouldRenderFace(world, worldX, y, worldZ, 0, 0, 1, block)) {
                            addSouthFace(transparentVertices, transparentTexCoords, transparentTints, transparentAnimationData, transparentIndices, worldX, y, worldZ, state, transparentVertexOffset);
                            transparentVertexOffset += 4;
                        }
                    } else {
                        if (shouldRenderFace(world, worldX, y, worldZ, 0, -1, 0, block)) {
                            addBottomFace(vertices, texCoords, tints, animationData, indices, worldX, y, worldZ, state, vertexOffset);
                            vertexOffset += 4;
                        }
                        if (shouldRenderFace(world, worldX, y, worldZ, 0, 1, 0, block)) {
                            addTopFace(vertices, texCoords, tints, animationData, indices, worldX, y, worldZ, state, vertexOffset);
                            vertexOffset += 4;
                        }
                        if (shouldRenderFace(world, worldX, y, worldZ, -1, 0, 0, block)) {
                            addWestFace(vertices, texCoords, tints, animationData, indices, worldX, y, worldZ, state, vertexOffset);
                            vertexOffset += 4;
                        }
                        if (shouldRenderFace(world, worldX, y, worldZ, 1, 0, 0, block)) {
                            addEastFace(vertices, texCoords, tints, animationData, indices, worldX, y, worldZ, state, vertexOffset);
                            vertexOffset += 4;
                        }
                        if (shouldRenderFace(world, worldX, y, worldZ, 0, 0, -1, block)) {
                            addNorthFace(vertices, texCoords, tints, animationData, indices, worldX, y, worldZ, state, vertexOffset);
                            vertexOffset += 4;
                        }
                        if (shouldRenderFace(world, worldX, y, worldZ, 0, 0, 1, block)) {
                            addSouthFace(vertices, texCoords, tints, animationData, indices, worldX, y, worldZ, state, vertexOffset);
                            vertexOffset += 4;
                        }
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

        needsUpdate = false;
        return new MeshGenerationResult(this, verticesArray, texCoordsArray, tintsArray, animationDataArray, indicesArray,
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

    public void uploadToGPU(MeshGenerationResult result) {
        if (mesh != null) {
            mesh.cleanup();
            mesh = null;
        }

        if (transparentMesh != null) {
            transparentMesh.cleanup();
            transparentMesh = null;
        }

        if (result.vertices.length > 0) {
            mesh = new Mesh(result.vertices, result.texCoords, result.tints, result.animationData, result.indices);
        }

        if (result.transparentVertices.length > 0) {
            transparentMesh = new Mesh(result.transparentVertices, result.transparentTexCoords, result.transparentTints, result.transparentAnimationData, result.transparentIndices);
        }

        state = ChunkState.READY;
    }

    private boolean shouldRenderFace(World world, int x, int y, int z, int dx, int dy, int dz, Block currentBlock) {
        int neighborStateId = world.getBlockState(x + dx, y + dy, z + dz);
        GlobalPalette palette = GlobalPalette.getInstance();
        BlockState neighborState = palette.getState(neighborStateId);
        Block neighborBlock = neighborState.getBlock();
        
        if (neighborStateId == AIR_STATE_ID) {
            return true;
        }
        
        boolean neighborIsSolid = neighborBlock.isSolid();
        boolean neighborIsFluid = neighborBlock.isFluid();
        
        if (!neighborIsSolid && !neighborIsFluid) {
            return true;
        }
        
        boolean currentIsSolid = currentBlock.isSolid();
        boolean currentIsFluid = currentBlock.isFluid();
        
        if (currentIsSolid && neighborIsFluid) {
            return true;
        }
        
        if (currentIsFluid && neighborIsSolid) {
            return true;
        }
        
        return false;
    }

    private void addTopFace(List<Float> vertices, List<Float> texCoords, List<Float> tints, List<Float> animationData, List<Integer> indices, int x, int y, int z, BlockState state, int offset) {
        float[] tex = getTextureCoords(state, 0);
        float u0 = tex[0], v0 = tex[1], u1 = tex[2], v1 = tex[3];
        
        vertices.add((float) x); vertices.add((float) (y + 1)); vertices.add((float) z);
        vertices.add((float) (x + 1)); vertices.add((float) (y + 1)); vertices.add((float) z);
        vertices.add((float) (x + 1)); vertices.add((float) (y + 1)); vertices.add((float) (z + 1));
        vertices.add((float) x); vertices.add((float) (y + 1)); vertices.add((float) (z + 1));

        texCoords.add(u0); texCoords.add(v1);
        texCoords.add(u1); texCoords.add(v1);
        texCoords.add(u1); texCoords.add(v0);
        texCoords.add(u0); texCoords.add(v0);

        float tintValue = getTintValue(state, 0);
        tints.add(tintValue);
        tints.add(tintValue);
        tints.add(tintValue);
        tints.add(tintValue);

        float[] animData = getAnimationData(state, 0);
        animationData.add(animData[0]);
        animationData.add(animData[1]);
        animationData.add(animData[2]);
        animationData.add(animData[0]);
        animationData.add(animData[1]);
        animationData.add(animData[2]);
        animationData.add(animData[0]);
        animationData.add(animData[1]);
        animationData.add(animData[2]);
        animationData.add(animData[0]);
        animationData.add(animData[1]);
        animationData.add(animData[2]);

        indices.add(offset); indices.add(offset + 3); indices.add(offset + 2);
        indices.add(offset); indices.add(offset + 2); indices.add(offset + 1);
    }

    private void addBottomFace(List<Float> vertices, List<Float> texCoords, List<Float> tints, List<Float> animationData, List<Integer> indices, int x, int y, int z, BlockState state, int offset) {
        float[] tex = getTextureCoords(state, 1);
        float u0 = tex[0], v0 = tex[1], u1 = tex[2], v1 = tex[3];
        
        vertices.add((float) x); vertices.add((float) y); vertices.add((float) z);
        vertices.add((float) (x + 1)); vertices.add((float) y); vertices.add((float) z);
        vertices.add((float) (x + 1)); vertices.add((float) y); vertices.add((float) (z + 1));
        vertices.add((float) x); vertices.add((float) y); vertices.add((float) (z + 1));

        texCoords.add(u0); texCoords.add(v0);
        texCoords.add(u1); texCoords.add(v0);
        texCoords.add(u1); texCoords.add(v1);
        texCoords.add(u0); texCoords.add(v1);

        float tintValue = getTintValue(state, 1);
        tints.add(tintValue);
        tints.add(tintValue);
        tints.add(tintValue);
        tints.add(tintValue);

        float[] animData = getAnimationData(state, 1);
        animationData.add(animData[0]);
        animationData.add(animData[1]);
        animationData.add(animData[2]);
        animationData.add(animData[0]);
        animationData.add(animData[1]);
        animationData.add(animData[2]);
        animationData.add(animData[0]);
        animationData.add(animData[1]);
        animationData.add(animData[2]);
        animationData.add(animData[0]);
        animationData.add(animData[1]);
        animationData.add(animData[2]);

        indices.add(offset); indices.add(offset + 1); indices.add(offset + 2);
        indices.add(offset); indices.add(offset + 2); indices.add(offset + 3);
    }

    private void addNorthFace(List<Float> vertices, List<Float> texCoords, List<Float> tints, List<Float> animationData, List<Integer> indices, int x, int y, int z, BlockState state, int offset) {
        float[] tex = getTextureCoords(state, 2);
        float u0 = tex[0], v0 = tex[1], u1 = tex[2], v1 = tex[3];
        
        vertices.add((float) x); vertices.add((float) y); vertices.add((float) z);
        vertices.add((float) (x + 1)); vertices.add((float) y); vertices.add((float) z);
        vertices.add((float) (x + 1)); vertices.add((float) (y + 1)); vertices.add((float) z);
        vertices.add((float) x); vertices.add((float) (y + 1)); vertices.add((float) z);

        texCoords.add(u0); texCoords.add(v1);
        texCoords.add(u1); texCoords.add(v1);
        texCoords.add(u1); texCoords.add(v0);
        texCoords.add(u0); texCoords.add(v0);

        float tintValue = getTintValue(state, 2);
        tints.add(tintValue);
        tints.add(tintValue);
        tints.add(tintValue);
        tints.add(tintValue);

        float[] animData = getAnimationData(state, 2);
        animationData.add(animData[0]);
        animationData.add(animData[1]);
        animationData.add(animData[2]);
        animationData.add(animData[0]);
        animationData.add(animData[1]);
        animationData.add(animData[2]);
        animationData.add(animData[0]);
        animationData.add(animData[1]);
        animationData.add(animData[2]);
        animationData.add(animData[0]);
        animationData.add(animData[1]);
        animationData.add(animData[2]);

        indices.add(offset); indices.add(offset + 3); indices.add(offset + 2);
        indices.add(offset); indices.add(offset + 2); indices.add(offset + 1);
    }

    private void addSouthFace(List<Float> vertices, List<Float> texCoords, List<Float> tints, List<Float> animationData, List<Integer> indices, int x, int y, int z, BlockState state, int offset) {
        float[] tex = getTextureCoords(state, 2);
        float u0 = tex[0], v0 = tex[1], u1 = tex[2], v1 = tex[3];
        
        vertices.add((float) (x + 1)); vertices.add((float) y); vertices.add((float) (z + 1));
        vertices.add((float) x); vertices.add((float) y); vertices.add((float) (z + 1));
        vertices.add((float) x); vertices.add((float) (y + 1)); vertices.add((float) (z + 1));
        vertices.add((float) (x + 1)); vertices.add((float) (y + 1)); vertices.add((float) (z + 1));

        texCoords.add(u0); texCoords.add(v1);
        texCoords.add(u1); texCoords.add(v1);
        texCoords.add(u1); texCoords.add(v0);
        texCoords.add(u0); texCoords.add(v0);

        float tintValue = getTintValue(state, 2);
        tints.add(tintValue);
        tints.add(tintValue);
        tints.add(tintValue);
        tints.add(tintValue);

        float[] animData = getAnimationData(state, 2);
        animationData.add(animData[0]);
        animationData.add(animData[1]);
        animationData.add(animData[2]);
        animationData.add(animData[0]);
        animationData.add(animData[1]);
        animationData.add(animData[2]);
        animationData.add(animData[0]);
        animationData.add(animData[1]);
        animationData.add(animData[2]);
        animationData.add(animData[0]);
        animationData.add(animData[1]);
        animationData.add(animData[2]);

        indices.add(offset); indices.add(offset + 3); indices.add(offset + 2);
        indices.add(offset); indices.add(offset + 2); indices.add(offset + 1);
    }

    private void addWestFace(List<Float> vertices, List<Float> texCoords, List<Float> tints, List<Float> animationData, List<Integer> indices, int x, int y, int z, BlockState state, int offset) {
        float[] tex = getTextureCoords(state, 2);
        float u0 = tex[0], v0 = tex[1], u1 = tex[2], v1 = tex[3];
        
        vertices.add((float) x); vertices.add((float) y); vertices.add((float) (z + 1));
        vertices.add((float) x); vertices.add((float) y); vertices.add((float) z);
        vertices.add((float) x); vertices.add((float) (y + 1)); vertices.add((float) z);
        vertices.add((float) x); vertices.add((float) (y + 1)); vertices.add((float) (z + 1));

        texCoords.add(u0); texCoords.add(v1);
        texCoords.add(u1); texCoords.add(v1);
        texCoords.add(u1); texCoords.add(v0);
        texCoords.add(u0); texCoords.add(v0);

        float tintValue = getTintValue(state, 2);
        tints.add(tintValue);
        tints.add(tintValue);
        tints.add(tintValue);
        tints.add(tintValue);

        float[] animData = getAnimationData(state, 2);
        animationData.add(animData[0]);
        animationData.add(animData[1]);
        animationData.add(animData[2]);
        animationData.add(animData[0]);
        animationData.add(animData[1]);
        animationData.add(animData[2]);
        animationData.add(animData[0]);
        animationData.add(animData[1]);
        animationData.add(animData[2]);
        animationData.add(animData[0]);
        animationData.add(animData[1]);
        animationData.add(animData[2]);

        indices.add(offset); indices.add(offset + 3); indices.add(offset + 2);
        indices.add(offset); indices.add(offset + 2); indices.add(offset + 1);
    }

    private void addEastFace(List<Float> vertices, List<Float> texCoords, List<Float> tints, List<Float> animationData, List<Integer> indices, int x, int y, int z, BlockState state, int offset) {
        float[] tex = getTextureCoords(state, 2);
        float u0 = tex[0], v0 = tex[1], u1 = tex[2], v1 = tex[3];
        
        vertices.add((float) (x + 1)); vertices.add((float) y); vertices.add((float) z);
        vertices.add((float) (x + 1)); vertices.add((float) y); vertices.add((float) (z + 1));
        vertices.add((float) (x + 1)); vertices.add((float) (y + 1)); vertices.add((float) (z + 1));
        vertices.add((float) (x + 1)); vertices.add((float) (y + 1)); vertices.add((float) z);

        texCoords.add(u0); texCoords.add(v1);
        texCoords.add(u1); texCoords.add(v1);
        texCoords.add(u1); texCoords.add(v0);
        texCoords.add(u0); texCoords.add(v0);

        float tintValue = getTintValue(state, 2);
        tints.add(tintValue);
        tints.add(tintValue);
        tints.add(tintValue);
        tints.add(tintValue);

        float[] animData = getAnimationData(state, 2);
        animationData.add(animData[0]);
        animationData.add(animData[1]);
        animationData.add(animData[2]);
        animationData.add(animData[0]);
        animationData.add(animData[1]);
        animationData.add(animData[2]);
        animationData.add(animData[0]);
        animationData.add(animData[1]);
        animationData.add(animData[2]);
        animationData.add(animData[0]);
        animationData.add(animData[1]);
        animationData.add(animData[2]);

        indices.add(offset); indices.add(offset + 3); indices.add(offset + 2);
        indices.add(offset); indices.add(offset + 2); indices.add(offset + 1);
    }

    private float[] getTextureCoords(BlockState state, int face) {
        Block block = state.getBlock();
        String textureName = block.getTextureName(face, state);
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
        Block block = state.getBlock();
        String textureName = block.getTextureName(face, state);
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
        Block block = state.getBlock();
        String textureName = block.getTextureName(face, state);
        if (textureName == null) {
            return 0.0f;
        }
        
        if (block.getNamespacedID().equals("mycraft:grass_block") && face == 0) {
            return 1.0f;
        }
        
        if (block.getNamespacedID().equals("mycraft:leaves")) {
            return 1.0f;
        }
        
        return 0.0f;
    }

    public void render() {
        if (mesh == null) {
            return;
        }
        mesh.render();
    }

    public void renderTransparent() {
        if (transparentMesh == null) {
            return;
        }
        transparentMesh.render();
    }
    
    public boolean hasMesh() {
        return mesh != null;
    }

    public boolean hasTransparentMesh() {
        return transparentMesh != null;
    }

    public void cleanup() {
        if (mesh != null) {
            mesh.cleanup();
        }
        if (transparentMesh != null) {
            transparentMesh.cleanup();
        }
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    public boolean needsUpdate() {
        return needsUpdate;
    }

    public ChunkState getState() {
        return state;
    }

    public void setState(ChunkState state) {
        this.state = state;
    }

    public BoundingBox getBoundingBox() {
        return boundingBox;
    }

    public void markDirty(boolean isDirty) {
        this.isDirty = isDirty;
    }

    public boolean isDirty() {
        return isDirty;
    }

    public void markEntitiesDirty(boolean entitiesDirty) {
        this.entitiesDirty = entitiesDirty;
    }

    public boolean areEntitiesDirty() {
        return entitiesDirty;
    }
}

