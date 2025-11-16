package com.ksptool.ourcraft.sharedcore.network.packets;

/**
 * 服务端发送给客户端卸载区块的命令 (Server Sync Unload Chunk Network View Object)
 */
public record ServerSyncUnloadChunkNVo(int chunkX, int chunkY, int chunkZ) {}

