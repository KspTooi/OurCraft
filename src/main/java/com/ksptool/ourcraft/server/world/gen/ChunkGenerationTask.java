package com.ksptool.ourcraft.server.world.gen;

import com.ksptool.ourcraft.server.world.chunk.ServerChunkOld;

/**
 * 区块生成任务类，封装区块生成任务的状态信息
 */
public class ChunkGenerationTask {

    //区块X坐标
    private final int chunkX;
    
    //区块Z坐标
    private final int chunkZ;

    //区块
    private ServerChunkOld chunk;
    
    //数据是否生成
    private boolean dataGenerated;

    public ChunkGenerationTask(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.dataGenerated = false;
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    public ServerChunkOld getChunk() {
        return chunk;
    }

    public void setChunk(ServerChunkOld chunk) {
        this.chunk = chunk;
    }

    public boolean isDataGenerated() {
        return dataGenerated;
    }

    public void setDataGenerated(boolean dataGenerated) {
        this.dataGenerated = dataGenerated;
    }
}

