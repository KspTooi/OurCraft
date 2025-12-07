package com.ksptool.ourcraft.debug;

import lombok.Getter;

/**
 * 调试客户端区块数据存储
 */
@Getter
public class DebugChunk {

    private final int chunkX;
    private final int chunkZ;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private int[][][] blockStates;

    public DebugChunk(int chunkX, int chunkZ, int sizeX, int sizeY, int sizeZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.blockStates = new int[sizeX][sizeY][sizeZ];
    }

    public void setBlockStates(int[][][] blockStates) {
        this.blockStates = blockStates;
    }

    public int getBlockState(int x, int y, int z) {
        if (blockStates == null) {
            return 0;
        }
        if (x >= 0 && x < blockStates.length &&
                y >= 0 && y < blockStates[0].length &&
                z >= 0 && z < blockStates[0][0].length) {
            return blockStates[x][y][z];
        }
        return 0;
    }

    public void setBlockState(int x, int y, int z, int stateId) {
        if (blockStates == null) {
            return;
        }
        if (x >= 0 && x < blockStates.length &&
                y >= 0 && y < blockStates[0].length &&
                z >= 0 && z < blockStates[0][0].length) {
            blockStates[x][y][z] = stateId;
        }
    }
}
