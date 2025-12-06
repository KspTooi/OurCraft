package com.ksptool.ourcraft.server.world.chunk;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import com.ksptool.ourcraft.server.OurCraftServer;
import com.ksptool.ourcraft.server.archive.ArchiveSuperChunkService;
import com.ksptool.ourcraft.server.event.ServerChunkReadyEvent;
import com.ksptool.ourcraft.server.event.ServerChunkUnloadedEvent;
import com.ksptool.ourcraft.server.world.ServerWorld;
import com.ksptool.ourcraft.sharedcore.utils.position.ChunkPos;
import com.ksptool.ourcraft.sharedcore.utils.position.Pos;
import com.ksptool.ourcraft.sharedcore.world.BlockState;
import com.ksptool.ourcraft.sharedcore.world.WorldService;
import com.ksptool.ourcraft.sharedcore.world.SharedWorld;

/**
 * Flex区块管理器，负责区块的加载、卸载、缓存和存盘
 * 新一代的区块管理器 负责接替Simple系列组件
 */
@Slf4j
public class FlexServerChunkService extends WorldService{

    //服务器实例
    private final OurCraftServer server;

    //世界实例
    private final ServerWorld world;

    //Flex区块->Flex区块
    private final Map<ChunkPos, FlexServerChunk> chunks = new ConcurrentHashMap<>();

    //区块卸载任务
    private final Map<ChunkPos, CompletableFuture<FlexServerChunk>> chunkUnloadMap = new ConcurrentHashMap<>();

    //归档区块管理器
    private final ArchiveSuperChunkService ascs;

    //区块租约服务
    private final FlexChunkLeaseService fcls;

    //区块大小X
    private final int chunkSizeX;

    //区块大小Z
    private final int chunkSizeZ;

    public FlexServerChunkService(OurCraftServer server, ServerWorld world) {

        this.server = server;
        this.world = world;
        ascs = server.getArchiveService().getChunkService();
        fcls = world.getFcls();

        if(!server.getArchiveService().isConnectedArchiveIndex()){
            throw new RuntimeException("未连接到归档索引,无法创建Flex区块管理器");
        }

        //从世界模板中获取区块大小
        var template = world.getTemplate();
        this.chunkSizeX = template.getChunkSizeX();
        this.chunkSizeZ = template.getChunkSizeZ();
    }

    /**
     * 执行更新
     * @param delta 距离上一帧经过的时间（秒）由SWEU传入
     * @param world 世界
     */
    @Override
    public void action(double delta, SharedWorld world) {

        //从租约服务拉取发生变化的区块
        var changedChunks = fcls.pollChanges();

        //遍历发生变化的区块,查询它们的租约等级
        for(var chunkPos : changedChunks){

            var level = fcls.getLeaseLevel(chunkPos);

            //无租约 异步卸载区块
            if(level == null){
                unloadAndSave(chunkPos);
                continue;
            }

            //有租约 异步加载区块
            if(!isChunkReady(chunkPos)){
                loadOrGenerate(chunkPos);
            }

        }
    }


    /**
     * 设置区块块
     * @param x 块坐标X
     * @param y 块坐标Y
     * @param z 块坐标Z
     * @param blockState 块状态
     */
    public void setBlockState(int x, int y, int z, BlockState blockState){
        var worldPos = Pos.of(x, y, z);
        var chunkPos = worldPos.toChunkPos(chunkSizeX, chunkSizeZ);
        var chunk = chunks.get(chunkPos);

        if(chunk == null){
            throw new RuntimeException("区块尚未完成初始化: X:" + x + " Y:" + y + " Z:" + z);
        }

        var localPos = worldPos.toLocalPos(chunkSizeX, chunkSizeZ);
        chunk.setBlockState(localPos.getX(), localPos.getY(), localPos.getZ(), blockState);
    }

    /**
     * 获取区块块
     * @param x 块坐标X
     * @param y 块坐标Y
     * @param z 块坐标Z
     * @return 块状态
     */
    public BlockState getBlockState(int x, int y, int z){
        var worldPos = Pos.of(x, y, z);
        var chunkPos = worldPos.toChunkPos(chunkSizeX, chunkSizeZ);
        var chunk = chunks.get(chunkPos);
        if(chunk == null){
            throw new RuntimeException("区块尚未完成初始化: X:" + x + " Y:" + y + " Z:" + z);
        }
        var localPos = worldPos.toLocalPos(chunkSizeX, chunkSizeZ);
        return chunk.getBlockState(localPos.getX(), localPos.getY(), localPos.getZ());
    }

    /**
     * 设置区块块（使用Pos）
     * @param pos 世界坐标
     * @param blockState 块状态
     */
    public void setBlockState(Pos pos, BlockState blockState){
        if(pos == null){
            throw new IllegalArgumentException("位置不能为空");
        }
        setBlockState(pos.getX(), pos.getY(), pos.getZ(), blockState);
    }

    /**
     * 获取区块块（使用Pos）
     * @param pos 世界坐标
     * @return 块状态
     */
    public BlockState getBlockState(Pos pos){
        if(pos == null){
            throw new IllegalArgumentException("位置不能为空");
        }
        return getBlockState(pos.getX(), pos.getY(), pos.getZ());
    }


    /**
     * 从SCA加载或生成区块数据(线程安全)
     * @param pos 块坐标
     * @return 区块加载任务
     */
    public CompletableFuture<FlexServerChunk> loadOrGenerate(ChunkPos pos) {

        // 使用 computeIfAbsent 实现原子性的"检查并初始化"
        // existsChunk 要么是刚生成的，要么是原本就有的，一定是同一个对象
        var chunk = chunks.computeIfAbsent(pos, _ -> {

            final var newChunk = new FlexServerChunk(pos, world);

            var tp = server.getCHUNK_PROCESS_THREAD_POOL();

            //提交异步任务
            tp.submit(() -> {
                try {
                    // 优先从归档SCA文件加载
                    if (ascs.hasChunk(world.getName(), pos)) {
                        var data = ascs.readChunk(world.getName(), pos);
                        var fcd = FlexChunkSerializer.deserialize(data);
                        newChunk.setFlexChunkData(fcd);
                        newChunk.setStage(FlexServerChunk.Stage.READY);
                        log.info("从SCA归档中加载区块: {}", pos);
                    }
                    // 归档不存在则生成
                    if(!ascs.hasChunk(world.getName(), pos)){
                        //直接用外层的 world 变量，避免变量名遮蔽
                        var tg = world.getTerrainGenerator();
                        tg.execute(newChunk, world.getGenerationContext());
                        newChunk.setStage(FlexServerChunk.Stage.READY);
                        log.info("生成新区块数据: {}", pos);
                    }

                    //成功完成 Future
                    newChunk.getLoadFuture().complete(newChunk);

                    //发布事件 (应放在 complete 之后，确保监听者拿到的是完成状态的 Future)
                    world.getSweb().publish(new ServerChunkReadyEvent(newChunk));

                } catch (Exception e) {
                    log.error("区块加载/生成失败: {}", pos, e);
                    newChunk.getLoadFuture().completeExceptionally(e);
                    // 可选：如果加载失败，可能需要从 chunks Map 中移除这个损坏的占位符区块
                    // chunks.remove(key);
                }
            });

            return newChunk;
        });

        return chunk.getLoadFuture();
    }


    /**
     * 卸载并保存区块数据(线程安全)
     * @param chunkPos 块坐标
     * @return 区块卸载任务
     */
    public CompletableFuture<FlexServerChunk> unloadAndSave(ChunkPos chunkPos) {
        // 使用 computeIfAbsent 实现原子性的"检查并初始化"
        // 如果已经存在卸载任务，直接返回同一个 Future
        return chunkUnloadMap.computeIfAbsent(chunkPos, pos -> {

            // 从 chunks Map 获取区块
            var chunk = chunks.get(pos);
            if(chunk == null){
                // 区块不存在，返回已完成的异常 Future
                var failedFuture = new CompletableFuture<FlexServerChunk>();
                failedFuture.completeExceptionally(new RuntimeException("区块不存在: " + pos));
                return failedFuture;
            }

            // 检查状态，只有 READY 状态的区块才能卸载
            if(chunk.getStage() != FlexServerChunk.Stage.READY){
                var failedFuture = new CompletableFuture<FlexServerChunk>();
                failedFuture.completeExceptionally(new RuntimeException("区块状态不正确，无法卸载: " + pos + ", 当前状态: " + chunk.getStage()));
                return failedFuture;
            }

            // 设置状态为 PROCESSING_UNLOAD
            chunk.setStage(FlexServerChunk.Stage.PROCESSING_UNLOAD);

            // 创建卸载 Future
            var newUnloadFuture = new CompletableFuture<FlexServerChunk>();

            var tp = server.getCHUNK_PROCESS_THREAD_POOL();

            // 提交异步任务
            tp.submit(() -> {
                try {
                    log.info("开始卸载区块: {}", pos);

                    // 如果区块是脏的，需要保存
                    if(chunk.isDirty()){
                        var fcd = chunk.getFlexChunkData();
                        var data = FlexChunkSerializer.serialize(fcd);
                        ascs.writeChunk(world.getName(), pos, data);
                        chunk.setDirty(false);
                        log.info("保存脏区块数据: {}", pos);
                    }

                    // 从 chunks Map 中移除区块
                    chunks.remove(pos);

                    // 设置状态为 INVALID
                    chunk.setStage(FlexServerChunk.Stage.INVALID);

                    // 成功完成 Future
                    newUnloadFuture.complete(chunk);

                    // 发布事件
                    world.getSweb().publish(new ServerChunkUnloadedEvent(chunk));

                    if(chunk.isDirty()){
                        log.info("区块卸载完成: {} 已保存", pos);
                    }
                    if(!chunk.isDirty()){
                        log.info("区块卸载完成: {} ", pos);
                    }

                } catch (Exception e) {
                    log.error("区块卸载/保存失败: {}", pos, e);
                    newUnloadFuture.completeExceptionally(e);
                } finally {
                    // 无论成功或失败，都从 chunkUnloadMap 中移除
                    chunkUnloadMap.remove(pos);
                }
            });

            return newUnloadFuture;
        });
    }





    /**
     * 判断一个区块是否完全加载完成
     * @param pos 块坐标
     */
    public boolean isChunkReady(ChunkPos pos){
        var chunk = chunks.get(pos);
        if(chunk == null){
            return false;
        }
        return chunk.getStage() == FlexServerChunk.Stage.READY;
    }




}
