package com.ksptool.ourcraft.server.world.chunk;

import lombok.extern.slf4j.Slf4j;
import java.util.Map;
import com.ksptool.ourcraft.server.OurCraftServer;
import com.ksptool.ourcraft.server.world.ServerWorld;


/**
 * 超级区块管理器，负责区块的加载、卸载、缓存和存盘
 */
@Slf4j
public class ServerSuperChunkManager {

    //服务器实例
    private final OurCraftServer server;

    //世界实例
    private final ServerWorld world;

    //超级区块
    private Map<Long, ServerChunk> chunks;

    public ServerSuperChunkManager(OurCraftServer server, ServerWorld world) {
        this.server = server;
        this.world = world;
    }


}
