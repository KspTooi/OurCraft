package com.ksptool.ourcraft.server;

import com.ksptool.ourcraft.server.archive.ArchiveService;
import com.ksptool.ourcraft.server.network.ServerNetworkService;
import com.ksptool.ourcraft.server.world.ServerWorldService;
import com.ksptool.ourcraft.server.world.gen.layers.BaseDensityLayer;
import com.ksptool.ourcraft.server.world.gen.layers.FeatureLayer;
import com.ksptool.ourcraft.server.world.gen.layers.SurfaceLayer;
import com.ksptool.ourcraft.server.world.gen.layers.WaterLayer;
import com.ksptool.ourcraft.sharedcore.GlobalPalette;
import com.ksptool.ourcraft.sharedcore.Registry;
import com.ksptool.ourcraft.sharedcore.enums.BlockEnums;
import com.ksptool.ourcraft.sharedcore.enums.EngineDefault;
import com.ksptool.ourcraft.sharedcore.enums.WorldTemplateEnums;
import com.ksptool.ourcraft.sharedcore.network.packets.*;
import com.ksptool.ourcraft.sharedcore.world.gen.DefaultTerrainGenerator;
import com.ksptool.ourcraft.sharedcore.world.gen.SpawnPlatformGenerator;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import com.ksptool.ourcraft.sharedcore.utils.ThreadFactoryUtils;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 服务端运行实例，负责逻辑更新（Tick-based loop）
 * 负责网络连接、玩家管理、世界管理等
 */
@Slf4j
@Getter
public class OurCraftServer {
    
    //服务端世界执行单元线程池(用于运行世界逻辑ACTION)
    private ExecutorService SWEU_THREAD_POOL;

    //区块工作线程池(用于处理区块加载、生成、卸载存盘等任务)
    private ExecutorService CHUNK_PROCESS_THREAD_POOL;

    //网络线程池(用于处理网络连接、心跳、数据包接收发送等任务(虚拟线程))
    private ExecutorService NETWORK_THREAD_POOL;


    private final String defaultWorldName = "earth_like";

    //世界服务
    private final ServerWorldService worldService;

    //归档管理器
    private final ArchiveService archiveService;

    //服务器配置服务
    private final ServerConfigService configService;

    //网络服务
    private final ServerNetworkService networkService;

    public OurCraftServer(String archiveName) {

        //初始化配置服务
        this.configService = new ServerConfigService(this);

        var _archiveName = archiveName;

        if (StringUtils.isBlank(_archiveName)) {
            _archiveName = "our_craft";
            log.warn("使用默认的归档: {}", _archiveName);
        }

        //注册全部原版内容
        this.registerAllDefaultContent();

        //初始化全局调色板
        GlobalPalette.getInstance().bake();

        //初始化归档管理器
        this.archiveService = new ArchiveService();

        //打开归档索引数据库连接
        archiveService.connectArchiveIndex(_archiveName);

        //初始化线程池
        initThreadPools();

        //创建世界服务
        this.worldService = new ServerWorldService(this);

        //创建玩家服务(旧实现,现在由网络服务代替)
        //this.playerService = new ServerPlayerService(this, NETWORK_PORT);

        //创建网络服务
        this.networkService = new ServerNetworkService(this);
    }

    public void start() {

        //创建世界
        worldService.createWorld(EngineDefault.DEFAULT_WORLD_NAME, "ourcraft:earth_like");

        //加载世界
        worldService.loadWorld(EngineDefault.DEFAULT_WORLD_NAME);

        //启动世界
        worldService.runWorld(EngineDefault.DEFAULT_WORLD_NAME);

        //启动网络服务
        networkService.start();
        //playerService.start();
    }



    /*private void handlePlayerInputState(PlayerInputStateNDto packet, PlayerSession handler) {
        if (!handler.isPlayerInitialized()) {
            log.debug("玩家尚未初始化完成，忽略输入");
            return;
        }

        ServerPlayer player = handler.getPlayer();
        if (player == null) {
            log.warn("客户端连接没有关联的玩家实体");
            return;
        }

        // 创建 PlayerInputEvent 并放入事件队列，由 WorldExecutionUnit 在 tick 中处理
        PlayerInputEvent inputEvent = new PlayerInputEvent(
                packet.w(),
                packet.s(),
                packet.a(),
                packet.d(),
                packet.space());

        // 将事件放入 C2S (Client to Server) 队列
        // 注意：这里假设 EventQueue 是线程安全的
        SimpleEventQueue.getInstance().offerC2S(inputEvent);

        // 相机更新也可以放入队列，或者暂时保持直接更新（视 EventQueue 支持的事件类型而定）
        // 为了保持一致性，建议也封装为事件，但目前先保留直接更新以最小化改动，
        // 只要 Player 对象是线程安全的（或 volatile 字段）。
        // 不过，ServerWorldExecutionUnit.processEvents 中有处理 PlayerCameraInputEvent 的逻辑，
        // 所以最好也转为事件。

        float currentYaw = (float)player.getYaw();
        float currentPitch = (float)player.getPitch();
        float deltaYaw = packet.yaw() - currentYaw;
        float deltaPitch = packet.pitch() - currentPitch;

        // 处理角度环绕（-180到180度）
        if (deltaYaw > 180.0f) {
            deltaYaw -= 360.0f;
        } else if (deltaYaw < -180.0f) {
            deltaYaw += 360.0f;
        }

        // 发送相机输入事件
        com.ksptool.ourcraft.sharedcore.events.PlayerCameraInputEvent cameraEvent = new com.ksptool.ourcraft.sharedcore.events.PlayerCameraInputEvent(
                deltaYaw, deltaPitch);
        SimpleEventQueue.getInstance().offerC2S(cameraEvent);
    }
*/



    /**
     * 正常关闭服务器
     * 这会 
     * 1. 停止所有世界
     * 2. 保存所有世界
     * 3. 保存当前依然在线的所有玩家
     */
    public void shutdown() {

        //关闭玩家服务
        //playerService.stop();
        networkService.shutdown();

        //停止所有世界的运行并将它们写入归档
        worldService.shutdown();

        //关闭线程池
        shutdownThreadPools();

        //断开归档索引数据库连接
        archiveService.disconnectArchiveIndex();
    }

    /**
     * 关闭所有线程池
     */
    private void shutdownThreadPools() {
        if (SWEU_THREAD_POOL != null) {
            SWEU_THREAD_POOL.shutdown();
            log.info("SWEU线程池已关闭");
        }
        if (CHUNK_PROCESS_THREAD_POOL != null) {
            CHUNK_PROCESS_THREAD_POOL.shutdown();
            log.info("区块处理线程池已关闭");
        }
        if (NETWORK_THREAD_POOL != null) {
            NETWORK_THREAD_POOL.shutdown();
            log.info("网络线程池已关闭");
        }
    }

    /**
     * 注册所有引擎原版的内容(服务端) 这包括方块、物品、世界模板、实体
     */
    public void registerAllDefaultContent() {

        var registry = Registry.getInstance();

        BlockEnums.registerBlocks(registry);
        WorldTemplateEnums.registerWorldTemplate(registry);

        //注册地形生成器
        var gen = new DefaultTerrainGenerator();
        gen.addLayer(new BaseDensityLayer());   //基础密度层
        gen.addLayer(new WaterLayer());         //水层
        gen.addLayer(new SurfaceLayer());       //表面层
        gen.addLayer(new FeatureLayer());       //装饰层
        registry.registerTerrainGenerator(gen); //注册地形生成器
        
        //注册出生平台生成器
        var spawnPlatformGen = new SpawnPlatformGenerator(); 
        registry.registerTerrainGenerator(spawnPlatformGen); 
    }

    /**
     * 初始化所有线程池
     */
    private void initThreadPools() {

        //初始化SWEU线程池（用于运行世界逻辑）
        SWEU_THREAD_POOL = new ThreadPoolExecutor(
            0,                             // 核心线程数
            EngineDefault.getMaxSWEUThreadCount() ,                          // 最大线程数 
            60L, TimeUnit.SECONDS,        // 闲置线程回收时间
            new LinkedBlockingQueue<>(EngineDefault.getMaxSWEUQueueSize()),    //最多排队任务
            ThreadFactoryUtils.createSWEUThreadFactory(), 
            new ThreadPoolExecutor.DiscardPolicy()   // 拒绝策略: 队列满丢弃任务
        );

        log.info("SWEU线程池已初始化 当前:{} 最大:{} 队列大小:{}", 0,EngineDefault.getMaxSWEUThreadCount(),EngineDefault.getMaxSWEUQueueSize());

        //初始化区块处理线程池（用于区块加载、生成、卸载）
        CHUNK_PROCESS_THREAD_POOL = new ThreadPoolExecutor(
            0,                             // 核心线程数
            EngineDefault.getMaxChunkProcessThreadCount(),                          // 最大线程数 
            60L, TimeUnit.SECONDS,        // 闲置线程回收时间
            new LinkedBlockingQueue<>(EngineDefault.getMaxChunkProcessQueueSize()),    //最多排队任务
            ThreadFactoryUtils.createChunkProcessThreadFactory(), 
            new ThreadPoolExecutor.DiscardPolicy()   // 拒绝策略: 队列满丢弃任务
        );

        log.info("区块处理线程池已初始化 当前:{} 最大:{} 队列大小:{}", 0,EngineDefault.getMaxChunkProcessThreadCount(),EngineDefault.getMaxChunkProcessQueueSize());

        //初始化网络线程池（虚拟线程，用于网络IO）
        NETWORK_THREAD_POOL = Executors.newThreadPerTaskExecutor(ThreadFactoryUtils.createNetworkThreadFactory());
        log.info("网络线程池已初始化(VT) 当前:{} 最大:{} 队列大小:{}", -1,-1,-1);
    }


}