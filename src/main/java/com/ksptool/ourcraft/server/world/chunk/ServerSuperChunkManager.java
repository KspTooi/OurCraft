package com.ksptool.ourcraft.server.world.chunk;

import lombok.extern.slf4j.Slf4j;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.ksptool.ourcraft.server.OurCraftServer;
import com.ksptool.ourcraft.server.archive.ArchiveSuperChunkManager;
import com.ksptool.ourcraft.server.world.ServerWorld;
import com.ksptool.ourcraft.sharedcore.world.BlockState;


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
    private final Map<Long, ServerSuperChunk> chunks = new ConcurrentHashMap<>();

    //归档区块管理器
    private ArchiveSuperChunkManager ascm;


    public ServerSuperChunkManager(OurCraftServer server, ServerWorld world) {
        this.server = server;
        this.world = world;
        ascm = server.getArchiveManager().getChunkManager();

        if(!server.getArchiveManager().isConnectedArchiveIndex()){
            throw new RuntimeException("未连接到归档索引,无法创建超级区块管理器");
        }

        //从世界模板中获取区块生成管道
        
    }

    /**
     * 设置区块方块
     * @param x 方块坐标X
     * @param y 方块坐标Y
     * @param z 方块坐标Z
     * @param blockState 方块状态
     */
    public void setBlockState(int x, int y, int z, BlockState blockState){
        
    }

    /**
     * 获取区块方块
     * @param x 方块坐标X
     * @param y 方块坐标Y
     * @param z 方块坐标Z
     * @return 方块状态
     */
    public BlockState getBlockState(int x, int y, int z){
        return null;
    }



}
