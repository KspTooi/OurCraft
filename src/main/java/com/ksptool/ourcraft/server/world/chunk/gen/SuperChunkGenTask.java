package com.ksptool.ourcraft.server.world.chunk.gen;

import com.ksptool.ourcraft.server.world.chunk.ServerSuperChunk;
import lombok.Getter;

/**
 * 超级区块生成任务类，封装区块生成任务的状态信息
 */
@Getter
public class SuperChunkGenTask {

    //区块X坐标
    private final int chunkX;

    //区块Z坐标
    private final int chunkZ;

    //超级区块
    private final ServerSuperChunk chunk;

    public SuperChunkGenTask(int chunkX, int chunkZ, ServerSuperChunk chunk) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.chunk = chunk;
    }

}
