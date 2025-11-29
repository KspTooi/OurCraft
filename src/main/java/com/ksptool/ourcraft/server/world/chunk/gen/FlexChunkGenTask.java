package com.ksptool.ourcraft.server.world.chunk.gen;

import com.ksptool.ourcraft.server.world.chunk.FlexServerChunk;
import lombok.Getter;

/**
 * 超级区块生成任务类，封装区块生成任务的状态信息
 */
@Getter
public class FlexChunkGenTask {

    //区块X坐标
    private final int chunkX;

    //区块Z坐标
    private final int chunkZ;

    //超级区块
    private final FlexServerChunk chunk;

    public FlexChunkGenTask(int chunkX, int chunkZ, FlexServerChunk chunk) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.chunk = chunk;
    }

}
