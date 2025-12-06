package com.ksptool.ourcraft.sharedcore.network.nvo;

/*
* 热更新区块数据网络视图对象 (Hot Update Chunk Network View Object)
*/
public record HuChunkNVo(int chunkX, int chunkZ, byte[] blockData) {

    public static HuChunkNVo of(int chunkX, int chunkZ, byte[] blockData) {
        return new HuChunkNVo(chunkX, chunkZ, blockData);
    }

}
