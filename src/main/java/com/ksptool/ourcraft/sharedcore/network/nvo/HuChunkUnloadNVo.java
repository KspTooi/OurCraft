package com.ksptool.ourcraft.sharedcore.network.nvo;

import com.ksptool.ourcraft.sharedcore.utils.position.ChunkPos;

/**
 * 服务端发送给客户端卸载区块的命令 (Server Sync Unload Chunk Network View Object)
 */
public record HuChunkUnloadNVo(ChunkPos pos) {}

