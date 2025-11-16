package com.ksptool.mycraft.sharedcore.events;

/**
 * 区块数据事件，用于服务端向客户端同步纯方块数据
 */
public class ChunkDataEvent extends GameEvent {
    private final int chunkX;
    private final int chunkZ;
    private final int[][][] blockStates;
    
    public ChunkDataEvent(int chunkX, int chunkZ, int[][][] blockStates) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.blockStates = blockStates;
    }
    
    public int getChunkX() {
        return chunkX;
    }
    
    public int getChunkZ() {
        return chunkZ;
    }
    
    public int[][][] getBlockStates() {
        return blockStates;
    }
}

