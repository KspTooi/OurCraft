package com.ksptool.ourcraft.server.world.chunk;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import com.ksptool.ourcraft.server.OurCraftServer;
import com.ksptool.ourcraft.server.archive.ArchiveSuperChunkService;
import com.ksptool.ourcraft.server.event.ServerChunkReadyEvent;
import com.ksptool.ourcraft.server.world.ServerWorld;
import com.ksptool.ourcraft.sharedcore.utils.ChunkUtils;
import com.ksptool.ourcraft.sharedcore.utils.position.ChunkPos;
import com.ksptool.ourcraft.sharedcore.world.BlockState;

/**
 * 超级区块管理器，负责区块的加载、卸载、缓存和存盘
 */
@Slf4j
public class FlexServerChunkService {

    //服务器实例
    private final OurCraftServer server;

    //世界实例
    private final ServerWorld world;

    //超级区块 区块Key(可通过ChunkUtils.getChunkKey(x,z)获取)->超级区块
    private final Map<Long, FlexServerChunk> chunks = new ConcurrentHashMap<>();

    //归档区块管理器
    private ArchiveSuperChunkService ascm;

    //玩家视距
    private final int playerRenderDistance = 8;


    public FlexServerChunkService(OurCraftServer server, ServerWorld world) {
        this.server = server;
        this.world = world;
        ascm = server.getArchiveService().getChunkService();

        if(!server.getArchiveService().isConnectedArchiveIndex()){
            throw new RuntimeException("未连接到归档索引,无法创建超级区块管理器");
        }

        //从世界模板中获取区块生成管道
        //TerrainGenerator terrainGenerator = world.getTerrainGenerator();
    }

    /**
     * 设置区块方块
     * @param x 块坐标X
     * @param y 块坐标Y
     * @param z 块坐标Z
     * @param blockState 块状态
     */
    public void setBlockState(int x, int y, int z, BlockState blockState){

        var chunkX = getChunkX(x);
        var chunkZ = getChunkZ(z);
        var key = ChunkUtils.getChunkKey(chunkX, chunkZ);
        var chunk = chunks.get(key);

        if(chunk == null){
            throw new RuntimeException("区块尚未完成初始化: X:" + x + " Y:" + y + " Z:" + z);
        }

        int localX = x - chunkX * world.getTemplate().getChunkSizeX();
        int localZ = z - chunkZ * world.getTemplate().getChunkSizeZ();
        chunk.setBlockState(localX, y, localZ, blockState);
    }

    /**
     * 获取区块方块
     * @param x 块坐标X
     * @param y 块坐标Y
     * @param z 块坐标Z
     * @return 块状态
     */
    public BlockState getBlockState(int x, int y, int z){
        var chunkX = getChunkX(x);
        var chunkZ = getChunkZ(z);
        var key = ChunkUtils.getChunkKey(chunkX, chunkZ);
        var chunk = chunks.get(key);
        if(chunk == null){
            throw new RuntimeException("区块尚未完成初始化: X:" + x + " Y:" + y + " Z:" + z);
        }
        int localX = x - chunkX * world.getTemplate().getChunkSizeX();
        int localZ = z - chunkZ * world.getTemplate().getChunkSizeZ();
        return chunk.getBlockState(localX, y, localZ);
    }


    /**
     * 从SCA加载或生成区块数据
     * @param pos 块坐标
     * @return 区块加载任务
     */
    public CompletableFuture<FlexServerChunk> loadOrGenerate(ChunkPos pos){

        //判断FSCM缓存中是否已有该区块
        FlexServerChunk chunk = chunks.get(pos.getChunkKey());

        //已有该区块 直接返回加载任务
        if(chunk != null){
            return chunk.getLoadFuture();
        }

        //没有该区块、创建新区块引用并加载或生成数据
        final var newChunk = new FlexServerChunk(pos, world);
        chunks.put(pos.getChunkKey(), chunk);

        var tp = server.getCHUNK_PROCESS_THREAD_POOL();

        //向线程池提交区块加载任务
        tp.submit(()->{

            //优先从SCAF归档中加载  判断SCAF中是否存在该区块
            if(ascm.hasChunk(world.getName(), pos)){

                //存在则加载区块数据
                var data = ascm.readChunk(world.getName(), pos);
                var fcd = FlexChunkSerializer.deserialize(data);
                newChunk.setFlexChunkData(fcd);
                newChunk.setStage(FlexServerChunk.Stage.READY);
                log.info("从SCA归档中加载区块数据: {}", pos);

                //完成异步加载任务 并发布区块就绪事件
                newChunk.getLoadFuture().complete(chunk);
                world.getEventBus().publish(new ServerChunkReadyEvent(newChunk));
                return newChunk;
            }

            //SCAF中不存在该区块 则生成区块数据
            var world = newChunk.getWorld();
            var tg = world.getTerrainGenerator();
            tg.execute(newChunk, world.getGenerationContext());
            newChunk.setStage(FlexServerChunk.Stage.READY);
            log.info("生成区块数据: {}", pos);
            //完成异步加载任务 并发布区块就绪事件
            newChunk.getLoadFuture().complete(newChunk);
            world.getEventBus().publish(new ServerChunkReadyEvent(newChunk));
            return newChunk;
        });

        return newChunk.getLoadFuture();
    }

    /**
     * 判断一个区块是否完全加载完成
     * @param pos 块坐标
     */
    public boolean isChunkReady(ChunkPos pos){
        var key = ChunkUtils.getChunkKey(pos.getX(), pos.getZ());
        var chunk = chunks.get(key);
        if(chunk == null){
            return false;
        }
        return chunk.getStage() == FlexServerChunk.Stage.READY;
    }


    /**
     * 获取区块X坐标
     * @param x 块坐标X
     * @return 区块X坐标
     */
    public int getChunkX(int x){
        var t = world.getTemplate();
        var chunkXSize = t.getChunkSizeX();
        return (int) Math.floor((float) x / chunkXSize);
    }

    /**
     * 获取区块Z坐标
     * @param z 块坐标Z
     * @return 区块Z坐标
     */
    public int getChunkZ(int z){
        var t = world.getTemplate();
        var chunkZSize = t.getChunkSizeZ();
        return (int) Math.floor((float) z / chunkZSize);
    }


    /**
     * 处理每帧的更新
     */
    public void update() {
        // 1. 获取所有在线玩家并更新其视距内的区块


        // 2. 检查并处理异步生成完成的区块


        // 3. 处理区块卸载 (垃圾回收)
    }


}
