package com.ksptool.ourcraft.sharedcore.network.packets;

/**
 * 服务端发送给客户端一个完整的区块数据 (Server Sync Chunk Data Network View Object)
 */
@Deprecated
public record ServerSyncChunkDataNVo(int chunkX, int chunkY, int chunkZ, byte[] blockData) {}

