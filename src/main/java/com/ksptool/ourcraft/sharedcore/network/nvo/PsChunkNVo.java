package com.ksptool.ourcraft.sharedcore.network.nvo;

/**
 * 进程切换区块数据(Process Switch Chunk Network View Object)
 */
public record PsChunkNVo(int chunkX, int chunkZ, byte[] blockData) {

    public static PsChunkNVo of(int chunkX, int chunkZ, byte[] blockData) {
        return new PsChunkNVo(chunkX, chunkZ, blockData);
    }

}
