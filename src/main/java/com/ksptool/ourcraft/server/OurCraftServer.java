package com.ksptool.ourcraft.server;

import com.ksptool.ourcraft.server.archive.ArchiveService;
import com.ksptool.ourcraft.server.archive.ArchivePlayerService;
import com.ksptool.ourcraft.server.archive.model.ArchivePlayerDto;
import com.ksptool.ourcraft.server.entity.ServerPlayer;
import com.ksptool.ourcraft.server.world.ServerWorldService;
import com.ksptool.ourcraft.server.player.PlayerSession;
import com.ksptool.ourcraft.server.player.ServerPlayerService;
import com.ksptool.ourcraft.server.world.chunk.SimpleServerChunk;
import com.ksptool.ourcraft.server.world.gen.layers.BaseDensityLayer;
import com.ksptool.ourcraft.server.world.gen.layers.FeatureLayer;
import com.ksptool.ourcraft.server.world.gen.layers.SurfaceLayer;
import com.ksptool.ourcraft.server.world.gen.layers.WaterLayer;
import com.ksptool.ourcraft.sharedcore.GlobalPalette;
import com.ksptool.ourcraft.sharedcore.Registry;
import com.ksptool.ourcraft.sharedcore.enums.BlockEnums;
import com.ksptool.ourcraft.sharedcore.enums.EngineDefault;
import com.ksptool.ourcraft.sharedcore.enums.WorldTemplateEnums;
import com.ksptool.ourcraft.sharedcore.events.PlayerInputEvent;
import com.ksptool.ourcraft.sharedcore.network.packets.*;
import com.ksptool.ourcraft.sharedcore.world.gen.DefaultTerrainGenerator;
import com.ksptool.ourcraft.sharedcore.world.gen.SpawnPlatformGenerator;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.joml.Vector3f;
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
    
    private static final int INITIAL_RENDER_DISTANCE = 8;

    private static final int NETWORK_PORT = 25564;

    private final String defaultWorldName = "earth_like";

    //世界服务
    private final ServerWorldService worldService;

    //归档管理器
    private final ArchiveService archiveService;

    //玩家服务
    private final ServerPlayerService playerService;

    public OurCraftServer(String archiveName) {

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

        //创建玩家服务
        this.playerService = new ServerPlayerService(this, NETWORK_PORT);
    }

    public void start() {

        //worldManager.createWorld(EngineDefault.DEFAULT_WORLD_NAME, EngineDefault.DEFAULT_WORLD_TEMPLATE);

        //创建世界
        worldService.createWorld(EngineDefault.DEFAULT_WORLD_NAME, "ourcraft:earth_like");

        //加载世界
        worldService.loadWorld(EngineDefault.DEFAULT_WORLD_NAME);

        //启动世界
        worldService.runWorld(EngineDefault.DEFAULT_WORLD_NAME);

        //启动玩家服务
        playerService.start();
    }




    /**
     * 当客户端断开连接时调用，移除对应的Player实体
     */
    public void onClientDisconnected(PlayerSession handler) {
        ServerPlayer player = handler.getPlayer();
        if (player != null) {
            //String saveName = worldManager.getWorld(defaultWorldName).getSaveName();
            //if (saveName != null && defaultWorldName != null) {
            // SaveManager.getInstance().savePlayer(saveName, player.getUniqueId(), player);
            //    log.info("已保存断开连接的玩家数据: UUID={}", player.getUniqueId());
            //}

            ArchivePlayerService playerManager = archiveService.getPlayerService();
            var dto = new ArchivePlayerDto();
            dto.setName(player.getName());
            dto.setPosX((double)player.getPosition().x);
            dto.setPosY((double)player.getPosition().y);
            dto.setPosZ((double)player.getPosition().z);
            dto.setYaw(player.getYaw());
            dto.setPitch(player.getPitch());
            dto.setHealth((int)player.getHealth());
            dto.setHungry((int)player.getHunger());
            dto.setExp(0L);
            playerManager.savePlayer(dto);

            log.info("移除断开连接的玩家实体");

            // 委托世界管理器移除玩家实体
            worldService.getWorld(defaultWorldName).removeEntity(player);
            handler.setPlayer(null);
        }
    }

 

    /**
     * 处理来自客户端的数据包
     * 这个方法由ClientConnectionHandler调用
     */
    public void handlePacket(PlayerSession handler, Object packet) {

        if (packet instanceof PlayerInputStateNDto) {
            handlePlayerInputState((PlayerInputStateNDto) packet, handler);
            return;
        }
        if (packet instanceof PlayerDcparNDto) {
            // 保留旧的位置同步包处理，用于向后兼容或调试
            handlePlayerPositionAndRotation((PlayerDcparNDto) packet, handler);
            return;
        }
        if (packet instanceof PlayerDshsNdto) {
            handlePlayerHotbarSwitch((PlayerDshsNdto) packet, handler);
            return;
        }
        if (packet instanceof PlayerDActionNDto) {
            handlePlayerAction((PlayerDActionNDto) packet, handler);
            return;
        }

        //该功能已由PlayerSession网络线程代替
        //if (packet instanceof RequestJoinServerNDto) {
        //    handleRequestJoinServer((RequestJoinServerNDto) packet, handler);
        //    return;
        //}

        if (packet instanceof ClientReadyNDto) {
            handleClientReady(handler);
            return;
        }

        //该功能已由PlayerSession网络线程代替
        //if (packet instanceof ClientKeepAliveNPkg) {
        //    // 心跳包，可以在这里更新最后心跳时间
        //    return;
        //}
        
        log.warn("收到未知类型的数据包: {}", packet.getClass().getName());
    }

    private void handlePlayerPositionAndRotation(PlayerDcparNDto packet, PlayerSession handler) {

        if (!handler.isPlayerInitialized()) {
            log.debug("玩家尚未初始化完成，忽略位置更新");
            return;
        }

        ServerPlayer player = handler.getPlayer();
        if (player == null) {
            log.warn("客户端连接没有关联的玩家实体");
            return;
        }

        Vector3f newPosition = new Vector3f((float) packet.x(), (float) packet.y(), (float) packet.z());
        Vector3f oldPosition = new Vector3f(player.getPosition());

        float distance = oldPosition.distance(newPosition);
        if (distance > 10.0f) {
            log.warn("玩家移动距离过大，可能作弊: distance={}, 服务器位置=({}, {}, {}), 客户端位置=({}, {}, {})",
                    distance, oldPosition.x, oldPosition.y, oldPosition.z, newPosition.x, newPosition.y, newPosition.z);

            // 检测到巨大位置差异，强制将客户端拉回服务器的权威位置
            // 创建一个数据包，包含服务器的当前（正确）位置
            ServerSyncEntityPositionAndRotationNVo correctionPacket = new ServerSyncEntityPositionAndRotationNVo(
                    0, // entityId 0 表示玩家自己
                    player.getPosition().x,
                    player.getPosition().y,
                    player.getPosition().z,
                    (float)player.getYaw(),
                    (float)player.getPitch());
            // 立即将纠正数据包发回给该客户端
            handler.sendPacket(correctionPacket);
            log.info("向客户端发送位置纠正包，强制同步到服务器位置");
            return; // 忽略这个错误的数据包，不更新服务器位置
        }

        player.getPosition().set(newPosition);
        player.setYaw((double)packet.yaw());
        player.setPitch((double)packet.pitch());
        player.markDirty(true);
    }

    private void handlePlayerHotbarSwitch(PlayerDshsNdto packet, PlayerSession handler) {
        ServerPlayer player = handler.getPlayer();
        if (player == null) {
            return;
        }
        int currentSlot = player.getInventory().getSelectedSlot();
        int slotDelta = packet.slotId() - currentSlot;
        if (slotDelta != 0) {
            player.getInventory().scrollSelection(slotDelta);
            player.markDirty(true);
        }
    }

    private void handlePlayerInputState(PlayerInputStateNDto packet, PlayerSession handler) {
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
        com.ksptool.ourcraft.sharedcore.events.EventQueue.getInstance().offerC2S(inputEvent);

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
        com.ksptool.ourcraft.sharedcore.events.EventQueue.getInstance().offerC2S(cameraEvent);
    }

    private void handlePlayerAction(PlayerDActionNDto packet, PlayerSession handler) {
        ServerPlayer player = handler.getPlayer();
        if (player == null) {
            return;
        }
        if (packet.actionType() == ActionType.FINISH_BREAKING) {
            player.handleBlockBreak();
        } else if (packet.actionType() == ActionType.PLACE_BLOCK) {
            player.handleBlockPlace();
        }
    }


    private void handleClientReady(PlayerSession handler) {
        // 客户端已准备好，执行初始同步
        ServerPlayer player = handler.getPlayer();
        if (worldService.getWorld(defaultWorldName) == null || player == null) {
            log.warn("无法执行初始同步: world={}, player={}", worldService.getWorld(defaultWorldName) != null, player != null);
            return;
        }

        log.info("开始为客户端执行初始区块同步，玩家位置: ({}, {}, {})",
                player.getPosition().x, player.getPosition().y, player.getPosition().z);
        performInitialSyncForClient(handler, player);

        // 初始同步完成后，标记玩家已初始化
        handler.setPlayerInitialized(true);
        log.info("玩家初始化完成，可以开始接收位置更新");
    }



    private void performInitialSyncForClient(PlayerSession handler, ServerPlayer player) {
        if (worldService.getWorld(defaultWorldName) == null || player == null) {
            return;
        }

        int playerChunkX = (int) Math
                .floor(player.getPosition().x / SimpleServerChunk.CHUNK_SIZE);
        int playerChunkZ = (int) Math
                .floor(player.getPosition().z / SimpleServerChunk.CHUNK_SIZE);

        for (int x = playerChunkX - INITIAL_RENDER_DISTANCE; x <= playerChunkX + INITIAL_RENDER_DISTANCE; x++) {
            for (int z = playerChunkZ - INITIAL_RENDER_DISTANCE; z <= playerChunkZ + INITIAL_RENDER_DISTANCE; z++) {
                SimpleServerChunk chunk = worldService.getWorld(defaultWorldName).getChunk(x, z);
                if (chunk == null) {
                    worldService.getWorld(defaultWorldName).generateChunkSynchronously(x, z);
                    chunk = worldService.getWorld(defaultWorldName).getChunk(x, z);
                }
                if (chunk != null) {
                    // 将区块数据转换为byte[]
                    int[][][] blockStates = new int[SimpleServerChunk.CHUNK_SIZE][SimpleServerChunk.CHUNK_HEIGHT][SimpleServerChunk.CHUNK_SIZE];
                    for (int localX = 0; localX < SimpleServerChunk.CHUNK_SIZE; localX++) {
                        for (int y = 0; y < SimpleServerChunk.CHUNK_HEIGHT; y++) {
                            for (int localZ = 0; localZ < SimpleServerChunk.CHUNK_SIZE; localZ++) {
                                blockStates[localX][y][localZ] = chunk.getBlockStateId(localX, y, localZ);
                            }
                        }
                    }
                    byte[] blockData = serializeChunkData(blockStates);
                    ServerSyncChunkDataNVo chunkPacket = new ServerSyncChunkDataNVo(x, 0, z, blockData);
                    handler.sendPacket(chunkPacket);
                }
            }
        }
    }

    /**
     * 获取所有连接的客户端处理器
     */
    public java.util.List<PlayerSession> getConnectedClients() {
        return new java.util.ArrayList<>(playerService.getClients());
    }

    /**
     * 将区块数据序列化为byte[]
     */
    private byte[] serializeChunkData(int[][][] blockStates) {
        int size = SimpleServerChunk.CHUNK_SIZE * SimpleServerChunk.CHUNK_HEIGHT * SimpleServerChunk.CHUNK_SIZE;
        byte[] data = new byte[size * 4];
        int index = 0;

        for (int x = 0; x < SimpleServerChunk.CHUNK_SIZE; x++) {
            for (int y = 0; y < SimpleServerChunk.CHUNK_HEIGHT; y++) {
                for (int z = 0; z < SimpleServerChunk.CHUNK_SIZE; z++) {
                    int stateId = blockStates[x][y][z];
                    data[index++] = (byte) (stateId & 0xFF);
                    data[index++] = (byte) ((stateId >> 8) & 0xFF);
                    data[index++] = (byte) ((stateId >> 16) & 0xFF);
                    data[index++] = (byte) ((stateId >> 24) & 0xFF);
                }
            }
        }

        return data;
    }


    /**
     * 正常关闭服务器
     * 这会 
     * 1. 停止所有世界
     * 2. 保存所有世界
     * 3. 保存当前依然在线的所有玩家
     */
    public void shutdown() {

        //关闭玩家服务
        playerService.stop();

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