package com.ksptool.mycraft.world;

import com.ksptool.mycraft.entity.BoundingBox;
import com.ksptool.mycraft.rendering.Mesh;
import com.ksptool.mycraft.rendering.TextureManager;

import java.util.ArrayList;
import java.util.List;

public class Chunk {
    public static final int CHUNK_SIZE = 16;
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
    private boolean needsUpdate;
    private ChunkState state;
    private BoundingBox boundingBox;
    private static final int AIR_STATE_ID = 0;

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
        List<Integer> indices = new ArrayList<>();

        int vertexOffset = 0;

        GlobalPalette palette = GlobalPalette.getInstance();

        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int y = 0; y < CHUNK_HEIGHT; y++) {
                for (int z = 0; z < CHUNK_SIZE; z++) {
                    int stateId = blockStates[x][y][z];
                    BlockState state = palette.getState(stateId);
                    Block block = state.getBlock();
                    
                    if (!block.isSolid()) {
                        continue;
                    }

                    int worldX = chunkX * CHUNK_SIZE + x;
                    int worldZ = chunkZ * CHUNK_SIZE + z;

                    if (shouldRenderFace(world, worldX, y, worldZ, 0, -1, 0)) {
                        addBottomFace(vertices, texCoords, tints, indices, worldX, y, worldZ, state, vertexOffset);
                        vertexOffset += 4;
                    }
                    if (shouldRenderFace(world, worldX, y, worldZ, 0, 1, 0)) {
                        addTopFace(vertices, texCoords, tints, indices, worldX, y, worldZ, state, vertexOffset);
                        vertexOffset += 4;
                    }
                    if (shouldRenderFace(world, worldX, y, worldZ, -1, 0, 0)) {
                        addWestFace(vertices, texCoords, tints, indices, worldX, y, worldZ, state, vertexOffset);
                        vertexOffset += 4;
                    }
                    if (shouldRenderFace(world, worldX, y, worldZ, 1, 0, 0)) {
                        addEastFace(vertices, texCoords, tints, indices, worldX, y, worldZ, state, vertexOffset);
                        vertexOffset += 4;
                    }
                    if (shouldRenderFace(world, worldX, y, worldZ, 0, 0, -1)) {
                        addNorthFace(vertices, texCoords, tints, indices, worldX, y, worldZ, state, vertexOffset);
                        vertexOffset += 4;
                    }
                    if (shouldRenderFace(world, worldX, y, worldZ, 0, 0, 1)) {
                        addSouthFace(vertices, texCoords, tints, indices, worldX, y, worldZ, state, vertexOffset);
                        vertexOffset += 4;
                    }
                }
            }
        }

        if (vertices.isEmpty()) {
            needsUpdate = false;
            return new MeshGenerationResult(this, new float[0], new float[0], new float[0], new int[0]);
        }

        float[] verticesArray = new float[vertices.size()];
        for (int i = 0; i < vertices.size(); i++) {
            verticesArray[i] = vertices.get(i);
        }

        float[] texCoordsArray = new float[texCoords.size()];
        for (int i = 0; i < texCoords.size(); i++) {
            texCoordsArray[i] = texCoords.get(i);
        }

        float[] tintsArray = new float[tints.size()];
        for (int i = 0; i < tints.size(); i++) {
            tintsArray[i] = tints.get(i);
        }

        int[] indicesArray = new int[indices.size()];
        for (int i = 0; i < indices.size(); i++) {
            indicesArray[i] = indices.get(i);
        }

        needsUpdate = false;
        return new MeshGenerationResult(this, verticesArray, texCoordsArray, tintsArray, indicesArray);
    }

    public void uploadToGPU(MeshGenerationResult result) {
        if (mesh != null) {
            mesh.cleanup();
            mesh = null;
        }

        if (result.vertices.length > 0) {
            mesh = new Mesh(result.vertices, result.texCoords, result.tints, result.indices);
        }

        state = ChunkState.READY;
    }

    private boolean shouldRenderFace(World world, int x, int y, int z, int dx, int dy, int dz) {
        int neighborStateId = world.getBlockState(x + dx, y + dy, z + dz);
        GlobalPalette palette = GlobalPalette.getInstance();
        BlockState neighborState = palette.getState(neighborStateId);
        Block neighborBlock = neighborState.getBlock();
        return !neighborBlock.isSolid();
    }

    private void addTopFace(List<Float> vertices, List<Float> texCoords, List<Float> tints, List<Integer> indices, int x, int y, int z, BlockState state, int offset) {
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

        Block block = state.getBlock();
        float tintValue = block.getNamespacedID().equals("mycraft:grass_block") ? 1.0f : 0.0f;
        tints.add(tintValue);
        tints.add(tintValue);
        tints.add(tintValue);
        tints.add(tintValue);

        indices.add(offset); indices.add(offset + 3); indices.add(offset + 2);
        indices.add(offset); indices.add(offset + 2); indices.add(offset + 1);
    }

    private void addBottomFace(List<Float> vertices, List<Float> texCoords, List<Float> tints, List<Integer> indices, int x, int y, int z, BlockState state, int offset) {
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

        tints.add(0.0f);
        tints.add(0.0f);
        tints.add(0.0f);
        tints.add(0.0f);

        indices.add(offset); indices.add(offset + 1); indices.add(offset + 2);
        indices.add(offset); indices.add(offset + 2); indices.add(offset + 3);
    }

    private void addNorthFace(List<Float> vertices, List<Float> texCoords, List<Float> tints, List<Integer> indices, int x, int y, int z, BlockState state, int offset) {
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

        tints.add(0.0f);
        tints.add(0.0f);
        tints.add(0.0f);
        tints.add(0.0f);

        indices.add(offset); indices.add(offset + 3); indices.add(offset + 2);
        indices.add(offset); indices.add(offset + 2); indices.add(offset + 1);
    }

    private void addSouthFace(List<Float> vertices, List<Float> texCoords, List<Float> tints, List<Integer> indices, int x, int y, int z, BlockState state, int offset) {
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

        tints.add(0.0f);
        tints.add(0.0f);
        tints.add(0.0f);
        tints.add(0.0f);

        indices.add(offset); indices.add(offset + 3); indices.add(offset + 2);
        indices.add(offset); indices.add(offset + 2); indices.add(offset + 1);
    }

    private void addWestFace(List<Float> vertices, List<Float> texCoords, List<Float> tints, List<Integer> indices, int x, int y, int z, BlockState state, int offset) {
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

        tints.add(0.0f);
        tints.add(0.0f);
        tints.add(0.0f);
        tints.add(0.0f);

        indices.add(offset); indices.add(offset + 3); indices.add(offset + 2);
        indices.add(offset); indices.add(offset + 2); indices.add(offset + 1);
    }

    private void addEastFace(List<Float> vertices, List<Float> texCoords, List<Float> tints, List<Integer> indices, int x, int y, int z, BlockState state, int offset) {
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

        tints.add(0.0f);
        tints.add(0.0f);
        tints.add(0.0f);
        tints.add(0.0f);

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

    public void render() {
        if (mesh == null) {
            return;
        }
        mesh.render();
    }
    
    public boolean hasMesh() {
        return mesh != null;
    }

    public void cleanup() {
        if (mesh != null) {
            mesh.cleanup();
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
}

