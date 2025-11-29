package com.ksptool.ourcraft.server.world.chunk;

import com.ksptool.ourcraft.server.entity.ServerEntity;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import com.ksptool.ourcraft.server.OurCraftServer;
import com.ksptool.ourcraft.server.archive.ArchiveSuperChunkManager;
import com.ksptool.ourcraft.server.entity.ServerPlayer;
import com.ksptool.ourcraft.server.world.ServerWorld;
import com.ksptool.ourcraft.server.world.chunk.gen.FlexChunkGenTask;
import com.ksptool.ourcraft.server.world.chunk.gen.FlexChunkGenerationThread;
import com.ksptool.ourcraft.sharedcore.enums.EngineDefault;
import com.ksptool.ourcraft.sharedcore.utils.ChunkUtils;
import com.ksptool.ourcraft.sharedcore.world.BlockState;

/**
 * 超级区块管理器，负责区块的加载、卸载、缓存和存盘
 */
@Slf4j
public class FlexServerChunkManager {

    //块生成队列(全局)
    private static final BlockingQueue<FlexChunkGenTask> generationQueue = new LinkedBlockingQueue<>();

    //块生成线程池(全局)
    private static final ExecutorService generationThreadPool;

    //服务器实例
    private final OurCraftServer server;

    //世界实例
    private final ServerWorld world;

    //超级区块 区块Key(可通过ChunkUtils.getChunkKey(x,z)获取)->超级区块
    private final Map<Long, FlexServerChunk> chunks = new ConcurrentHashMap<>();

    //归档区块管理器
    private ArchiveSuperChunkManager ascm;  

    //玩家视距
    private final int playerRenderDistance = 8;


    public FlexServerChunkManager(OurCraftServer server, ServerWorld world) {
        this.server = server;
        this.world = world;
        ascm = server.getArchiveManager().getChunkManager();

        if(!server.getArchiveManager().isConnectedArchiveIndex()){
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
     * @param chunkX 块坐标X
     * @param chunkZ 块坐标Y
     */
    public void loadOrGenerate(int chunkX, int chunkZ){

        //获取缓存区块数据
        var key = ChunkUtils.getChunkKey(chunkX, chunkZ);

        FlexServerChunk chunk = null;
        
        //优先从区块管理器缓存中获取
        chunk = chunks.get(key);

        if(chunk == null){
            //创建新区块引用
            chunk = new FlexServerChunk(chunkX, chunkZ, world);
            chunk.setState(FlexServerChunk.ChunkState.NEW);
            chunks.put(key, chunk);
        }

        var state = chunk.getState();

        //为NEW时优先从归档加载(同步)
        if(state == FlexServerChunk.ChunkState.NEW){

            try{
                var scaf = ascm.openSCAF(world.getName(), chunkX, chunkZ); //打开SCA文件

                //判断区块是否存在于SCA文件中
                if(scaf.hasChunk(chunkX, chunkZ)){
                    var data = scaf.readChunk(chunkX, chunkZ);

                    //解码区块为CompactBlockData
                    var cbd = FlexChunkSerializer.deserialize(data);
                    chunk.setRawBlockData(cbd); 
                    chunk.setState(FlexServerChunk.ChunkState.READY);
                    log.info("从SCA归档中加载区块数据: CX:{} CZ:{}", chunkX, chunkZ);
                }

                //区块不存在于SCA文件中 需提交异步生成任务
                if(!scaf.hasChunk(chunkX, chunkZ)){

                    //提交生成任务
                    generationQueue.add(new FlexChunkGenTask(chunkX, chunkZ, chunk));
                    log.info("提交异步区块生成任务: CX:{} CZ:{}", chunkX, chunkZ);

                    //设置区块状态为已提交生成任务
                    chunk.setState(FlexServerChunk.ChunkState.COMMITED_LOAD);
                }

            }catch(IOException e){
                throw new RuntimeException("从SCA归档中加载区块数据失败: " + key, e);
            }

        }

    }

    /**
     * 从SCA加载或生成区块数据
     * @param chunkX 块坐标X
     * @param chunkZ 块坐标Y
     */
    public void loadOrGenerateForce(int chunkX, int chunkZ){

        //获取缓存区块数据
        var key = ChunkUtils.getChunkKey(chunkX, chunkZ);

        FlexServerChunk chunk = null;

        //优先从区块管理器缓存中获取
        chunk = chunks.get(key);

        if(chunk == null){
            //创建新区块引用
            chunk = new FlexServerChunk(chunkX, chunkZ, world);
            chunk.setState(FlexServerChunk.ChunkState.NEW);
            chunks.put(key, chunk);
        }

        var state = chunk.getState();

        //为NEW时优先从归档加载(同步)
        if(state == FlexServerChunk.ChunkState.NEW){

            try{
                var scaf = ascm.openSCAF(world.getName(), chunkX, chunkZ); //打开SCA文件

                //判断区块是否存在于SCA文件中
                if(scaf.hasChunk(chunkX, chunkZ)){
                    var data = scaf.readChunk(chunkX, chunkZ);

                    //解码区块为CompactBlockData
                    var cbd = FlexChunkSerializer.deserialize(data);
                    chunk.setRawBlockData(cbd);
                    chunk.setState(FlexServerChunk.ChunkState.READY);
                    log.info("从SCA归档中加载区块数据: CX:{} CZ:{}", chunkX, chunkZ);
                }

                //区块不存在于SCA文件中 同步生成区块数据
                if(!scaf.hasChunk(chunkX, chunkZ)){
                    var world = chunk.getWorld();
                    var tg = world.getTerrainGenerator();
                    tg.execute(chunk, world.getGenerationContext());
                    chunk.setState(FlexServerChunk.ChunkState.FINISH_LOAD);
                    log.info("区块生成完成(同步): CX:{} CZ:{}", chunk.getX(), chunk.getZ());
                }

            }catch(IOException e){
                throw new RuntimeException("从SCA归档中加载区块数据失败: " + key, e);
            }

        }

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
        for (ServerEntity entity : world.getEntities()) {
            if (!(entity instanceof ServerPlayer)) continue;
            ServerPlayer player = (ServerPlayer) entity;
            updatePlayerView(player);
        }

        // 2. 检查并处理异步生成完成的区块
        for (FlexServerChunk chunk : chunks.values()) {
            if (chunk.getState() == FlexServerChunk.ChunkState.FINISH_LOAD) {
                chunk.setState(FlexServerChunk.ChunkState.READY);
                // TODO: 触发 "区块加载完成" 事件，通知相关系统发送数据包给 watchers
                log.info("区块加载完成: CX:{} CZ:{}", chunk.getX(), chunk.getZ());
            }
        }

        // 3. 处理区块卸载 (垃圾回收)
        Iterator<Map.Entry<Long, FlexServerChunk>> it = chunks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, FlexServerChunk> entry = it.next();
            FlexServerChunk chunk = entry.getValue();

            // 如果没有玩家关注该区块
            if (!chunk.hasWatchers()) {
                // 如果是脏区块，先保存
                if (chunk.isDirty()) {
                    saveChunk(chunk);
                }

                // 从内存中移除
                // 注意：这里可能需要一个"冷却时间"，防止玩家在边界反复横跳导致频繁IO
                it.remove();
                log.debug("卸载区块: {}, {}", chunk.getX(), chunk.getZ());
            }
        }
    }

    /**
     * 更新单个玩家的视距区块
     */
    private void updatePlayerView(ServerPlayer player) {
        int playerChunkX = getChunkX((int) player.getPosition().x);
        int playerChunkZ = getChunkZ((int) player.getPosition().z);

        // 计算玩家当前视距内的所有区块 Key
        Set<Long> inRangeKeys = new HashSet<>();
        for (int x = -playerRenderDistance; x <= playerRenderDistance; x++) {
            for (int z = -playerRenderDistance; z <= playerRenderDistance; z++) {
                inRangeKeys.add(ChunkUtils.getChunkKey(playerChunkX + x, playerChunkZ + z));
            }
        }

        // 1. 处理新进入视距的区块 (Load / Add Watcher)
        for (Long key : inRangeKeys) {
            int cx = ChunkUtils.getChunkX(key);
            int cz = ChunkUtils.getChunkZ(key);

            // 获取或创建区块（非强制同步加载，而是触发异步）
            FlexServerChunk chunk = getOrCreateChunk(cx, cz);

            // 添加玩家为观察者
            if (!chunk.getPlayersWatching().contains(player.getUniqueId().toString())) {
                chunk.addWatcher(player.getUniqueId().toString());
            }

            // 如果是新区块，触发加载
            if (chunk.getState() == FlexServerChunk.ChunkState.NEW) {
                loadOrGenerate(cx, cz);
            }
        }

        // 2. 处理离开视距的区块 (Remove Watcher)
        // 遍历所有区块，如果区块在玩家之前的视距内但不在现在的视距内，则移除Watcher
        // 这里简化实现，遍历玩家当前Watch的所有区块，如果不在inRangeKeys中则移除
        // 实际生产环境建议在Player对象中维护 watchedChunks 列表以提高性能
        for (FlexServerChunk chunk : chunks.values()) {
            if (chunk.getPlayersWatching().contains(player.getUniqueId().toString())) {
                long chunkKey = ChunkUtils.getChunkKey(chunk.getX(), chunk.getZ());
                if (!inRangeKeys.contains(chunkKey)) {
                    chunk.removeWatcher(player.getUniqueId().toString());
                }
            }
        }
    }

    // 辅助方法：保存区块
    private void saveChunk(FlexServerChunk chunk) {
        try {
            byte[] data = FlexChunkSerializer.serialize(chunk.getRawBlockData());
            var scaf = ascm.openSCAF(world.getName(), chunk.getX(), chunk.getZ());
            scaf.writeChunk(chunk.getX(), chunk.getZ(), data);
            chunk.setDirty(false); // 直接设置脏标记，注意 ServerSuperChunk 需要公开 setDirty 或者通过其他方式
        } catch (Exception e) {
            log.error("保存区块失败: {}, {}", chunk.getX(), chunk.getZ(), e);
        }
    }

    // 辅助方法：仅获取或创建对象，不触发加载 IO
    private FlexServerChunk getOrCreateChunk(int x, int z) {
        long key = ChunkUtils.getChunkKey(x, z);
        return chunks.computeIfAbsent(key, k -> {
            FlexServerChunk c = new FlexServerChunk(x, z, world);
            c.setState(FlexServerChunk.ChunkState.NEW);
            return c;
        });
    }

    static {

        var threadIndex = new AtomicInteger(0);

        generationThreadPool = Executors.newFixedThreadPool(EngineDefault.DEFAULT_BLOCK_GENERATION_THREAD_POOL_SIZE,r -> {
            Thread thread = new Thread(r);
            thread.setName("SSCGT-" + threadIndex.getAndIncrement()); //ServerSuperChunkGenerationThread
            return thread;
        });

        //初始化生成线程
        for (int i = 0; i < EngineDefault.DEFAULT_BLOCK_GENERATION_THREAD_POOL_SIZE; i++) {
            generationThreadPool.submit(new FlexChunkGenerationThread(generationQueue));
        }

    }

}
