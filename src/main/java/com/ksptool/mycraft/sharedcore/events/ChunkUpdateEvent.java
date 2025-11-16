package com.ksptool.mycraft.sharedcore.events;

/**
 * 区块更新事件，当区块数据改变时触发
 */
public class ChunkUpdateEvent extends GameEvent {
    private final int chunkX;
    private final int chunkZ;
    
    public ChunkUpdateEvent(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }
    
    public int getChunkX() {
        return chunkX;
    }
    
    public int getChunkZ() {
        return chunkZ;
    }
}

