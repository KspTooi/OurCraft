package com.ksptool.ourcraft.debug;

import lombok.Getter;

/**
 * 调试客户端区块数据存储
 */
@Getter
public class DebugChunk {
    
    public static final int CHUNK_SIZE = 16;
    public static final int CHUNK_HEIGHT = 256;
    
    private final int chunkX;
    private final int chunkZ;
    private int[][][] blockStates;
    
    public DebugChunk(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.blockStates = new int[CHUNK_SIZE][CHUNK_HEIGHT][CHUNK_SIZE];
    }
    
    public void setBlockStates(int[][][] blockStates) {
        this.blockStates = blockStates;
    }
    
    public int getBlockState(int x, int y, int z) {
        if (x >= 0 && x < CHUNK_SIZE && y >= 0 && y < CHUNK_HEIGHT && z >= 0 && z < CHUNK_SIZE) {
            return blockStates[x][y][z];
        }
        return 0;
    }
    
    public void setBlockState(int x, int y, int z, int stateId) {
        if (x >= 0 && x < CHUNK_SIZE && y >= 0 && y < CHUNK_HEIGHT && z >= 0 && z < CHUNK_SIZE) {
            blockStates[x][y][z] = stateId;
        }
    }
}

