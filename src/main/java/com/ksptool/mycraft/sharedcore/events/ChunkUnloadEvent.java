package com.ksptool.mycraft.sharedcore.events;

/**
 * 区块卸载事件，当区块离开玩家视距时触发
 */
public class ChunkUnloadEvent extends GameEvent {
    private final int chunkX;
    private final int chunkZ;

    public ChunkUnloadEvent(int chunkX, int chunkZ) {
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

