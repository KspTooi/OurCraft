package com.ksptool.mycraft.server.world;

import com.ksptool.mycraft.sharedcore.BoundingBox;

/**
 * 服务端区块类，只负责存储和管理方块数据，不包含任何渲染相关代码
 */
public class ServerChunk {

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
    private boolean needsUpdate;
    private ChunkState state;
    private BoundingBox boundingBox;
    private static final int AIR_STATE_ID = 0;
    private boolean isDirty = false;
    private boolean entitiesDirty = false;

    public ServerChunk(int chunkX, int chunkZ) {
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

    public void cleanup() {
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
    
    public int[][][] getBlockStates() {
        return blockStates;
    }
    
    public void setBlockStates(int[][][] blockStates) {
        this.blockStates = blockStates;
    }
}

