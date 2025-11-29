package com.ksptool.ourcraft.server;

import com.ksptool.ourcraft.server.archive.ArchiveManager;
import com.ksptool.ourcraft.server.archive.ArchivePlayerManager;
import com.ksptool.ourcraft.server.archive.model.ArchivePlayerDto;
import com.ksptool.ourcraft.server.archive.model.ArchivePlayerVo;
import com.ksptool.ourcraft.server.entity.ServerPlayer;
import com.ksptool.ourcraft.server.world.ServerWorldManager;
import com.ksptool.ourcraft.server.network.ClientConnectionHandler;
import com.ksptool.ourcraft.server.world.chunk.ServerChunkOld;
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
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 服务端运行实例，负责逻辑更新（Tick-based loop）
 * 负责网络连接、玩家管理、世界管理等
 */
@Slf4j
@Getter
public class OurCraftServer {

    private static final int INITIAL_RENDER_DISTANCE = 8;
    private static final int NETWORK_PORT = 25564;

    private final ServerWorldManager worldManager;

    private final String defaultWorldName = "earth_like";

    private Thread networkListenerThread;

    private ServerSocket serverSocket;

    private final CopyOnWriteArrayList<ClientConnectionHandler> connectedClients = new CopyOnWriteArrayList<>();

    private final ConcurrentHashMap<Integer, ClientConnectionHandler> sessionIdToHandler = new ConcurrentHashMap<>();

    //归档管理器
    private final ArchiveManager archiveManager;

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
        this.archiveManager = new ArchiveManager();

        //打开归档索引数据库连接
        archiveManager.connectArchiveIndex(_archiveName);

        //创建世界管理器
        this.worldManager = new ServerWorldManager(this);
    }

    public void start() {

        //worldManager.createWorld(EngineDefault.DEFAULT_WORLD_NAME, EngineDefault.DEFAULT_WORLD_TEMPLATE);

        //创建世界
        worldManager.createWorld(EngineDefault.DEFAULT_WORLD_NAME, "ourcraft:earth_like");

        //加载世界
        worldManager.loadWorld(EngineDefault.DEFAULT_WORLD_NAME);

        //启动世界
        worldManager.runWorld(EngineDefault.DEFAULT_WORLD_NAME);

        //启动网络监听器
        startNetworkListener();
    }

    /**
     * 当客户端断开连接时调用，移除对应的Player实体
     */
    public void onClientDisconnected(ClientConnectionHandler handler) {
        ServerPlayer player = handler.getPlayer();
        if (player != null) {
            //String saveName = worldManager.getWorld(defaultWorldName).getSaveName();
            //if (saveName != null && defaultWorldName != null) {
            // SaveManager.getInstance().savePlayer(saveName, player.getUniqueId(), player);
            //    log.info("已保存断开连接的玩家数据: UUID={}", player.getUniqueId());
            //}

            ArchivePlayerManager playerManager = archiveManager.getPlayerManager();
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
            dto.setCreateTime(java.time.LocalDateTime.now());

            playerManager.savePlayer(dto);

            log.info("移除断开连接的玩家实体");

            // 委托世界管理器移除玩家实体
            worldManager.getWorld(defaultWorldName).removeEntity(player);
            handler.setPlayer(null);
        }
    }

    /**
     * 启动网络监听器，监听客户端连接
     */
    private void startNetworkListener() {
        networkListenerThread = Thread.ofVirtual().start(() -> {
            try {
                serverSocket = new ServerSocket(NETWORK_PORT);
                log.info("网络监听器已启动，监听端口: {}", NETWORK_PORT);

                while (true) {

                    if(serverSocket.isClosed()){
                        break;
                    }

                    try {
                        Socket clientSocket = serverSocket.accept();
                        log.info("新的客户端连接: {}", clientSocket.getRemoteSocketAddress());

                        ClientConnectionHandler handler = new ClientConnectionHandler(clientSocket, this);
                        connectedClients.add(handler);
                        Thread.ofVirtual().start(handler);
                    } catch (IOException e) {
                        log.warn("接受客户端连接时发生错误: {}", e.getMessage());
                    }
                }
            } catch (IOException e) {
                log.error("启动网络监听器失败", e);
            } finally {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    try {
                        serverSocket.close();
                    } catch (IOException e) {
                        log.warn("关闭ServerSocket时发生错误: {}", e.getMessage());
                    }
                }
                log.info("网络监听器已停止");
            }
        });
    }

    /**
     * 停止网络监听器
     */
    private void stopNetworkListener() {
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                log.warn("关闭ServerSocket时发生错误: {}", e.getMessage());
            }
        }

        if (networkListenerThread != null) {
            try {
                networkListenerThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 处理来自客户端的数据包
     * 这个方法由ClientConnectionHandler调用
     */
    public void handlePacket(ClientConnectionHandler handler, Object packet) {
        if (packet instanceof PlayerInputStateNDto) {
            handlePlayerInputState((PlayerInputStateNDto) packet, handler);
        } else if (packet instanceof PlayerDcparNDto) {
            // 保留旧的位置同步包处理，用于向后兼容或调试
            handlePlayerPositionAndRotation((PlayerDcparNDto) packet, handler);
        } else if (packet instanceof PlayerDshsNdto) {
            handlePlayerHotbarSwitch((PlayerDshsNdto) packet, handler);
        } else if (packet instanceof PlayerDActionNDto) {
            handlePlayerAction((PlayerDActionNDto) packet, handler);
        } else if (packet instanceof RequestJoinServerNDto) {
            handleRequestJoinServer((RequestJoinServerNDto) packet, handler);
        } else if (packet instanceof ClientReadyNDto) {
            handleClientReady(handler);
        } else if (packet instanceof ClientKeepAliveNPkg) {
            // 心跳包，可以在这里更新最后心跳时间
        } else {
            log.warn("收到未知类型的数据包: {}", packet.getClass().getName());
        }
    }

    private void handlePlayerPositionAndRotation(PlayerDcparNDto packet, ClientConnectionHandler handler) {

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

    private void handlePlayerHotbarSwitch(PlayerDshsNdto packet, ClientConnectionHandler handler) {
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

    private void handlePlayerInputState(PlayerInputStateNDto packet, ClientConnectionHandler handler) {
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

    private void handlePlayerAction(PlayerDActionNDto packet, ClientConnectionHandler handler) {
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

    private void handleRequestJoinServer(RequestJoinServerNDto packet, ClientConnectionHandler handler) {
        log.info("收到客户端加入请求: clientVersion={}, playerName={}", packet.clientVersion(), packet.playerName());

        if (worldManager.getWorld(defaultWorldName) == null) {
            log.error("世界未初始化，无法接受玩家加入");
            RequestJoinServerNVo response = new RequestJoinServerNVo(
                    0, // rejected
                    "服务器世界未初始化",
                    null, null, null, null, null, null);
            handler.sendPacket(response);
            return;
        }

        ArchivePlayerManager playerManager = archiveManager.getPlayerManager();
        ArchivePlayerVo playerVo = playerManager.loadPlayer(packet.playerName());
        Vector3f spawnPos;

        if (playerVo == null) {
            Vector3f initialSpawnPos = new Vector3f(0, 64, 0);
            log.info("开始生成出生点周围的区块，确保地面存在");
            generateChunksAround(initialSpawnPos, INITIAL_RENDER_DISTANCE);
            int safeSpawnY = findSafeSpawnY((int) initialSpawnPos.x, (int) initialSpawnPos.z);
            spawnPos = new Vector3f(initialSpawnPos.x, safeSpawnY, initialSpawnPos.z);
            log.info("为新玩家 '{}' 计算得到安全出生点: ({}, {}, {})", packet.playerName(), spawnPos.x, spawnPos.y, spawnPos.z);

            var dto = new ArchivePlayerDto();
            dto.setName(packet.playerName());
            dto.setPosX((double)spawnPos.x);
            dto.setPosY((double)spawnPos.y);
            dto.setPosZ((double)spawnPos.z);
            dto.setYaw(0.0);
            dto.setPitch(0.0);
            dto.setHealth(40);
            dto.setHungry(40);
            dto.setExp(0L);
            dto.setCreateTime(java.time.LocalDateTime.now());

            playerVo = playerManager.savePlayer(dto);
            if (playerVo == null) {
                log.error("保存新玩家数据失败");
                RequestJoinServerNVo response = new RequestJoinServerNVo(
                        0, // rejected
                        "保存玩家数据失败",
                        null, null, null, null, null, null);
                handler.sendPacket(response);
                return;
            }
            log.info("为新玩家 '{}' 创建归档记录 UUID {}", packet.playerName(), playerVo.getUuid());
        } else {
            spawnPos = new Vector3f(
                    playerVo.getPosX() != null ? (float)playerVo.getPosX().doubleValue() : 0f,
                    playerVo.getPosY() != null ? (float)playerVo.getPosY().doubleValue() : 64f,
                    playerVo.getPosZ() != null ? (float)playerVo.getPosZ().doubleValue() : 0f
            );
            log.info("加载了玩家 '{}' 的数据, UUID: {}, 位置: ({}, {}, {})",
                    packet.playerName(), playerVo.getUuid(), spawnPos.x, spawnPos.y, spawnPos.z);
            generateChunksAround(spawnPos, INITIAL_RENDER_DISTANCE);
        }

        ServerPlayer newPlayer = new ServerPlayer(worldManager.getWorld(defaultWorldName), playerVo);
        worldManager.getWorld(defaultWorldName).addEntity(newPlayer);
        handler.setPlayer(newPlayer);

        int sessionId = connectedClients.indexOf(handler) + 1;
        sessionIdToHandler.put(sessionId, handler);

        RequestJoinServerNVo response = new RequestJoinServerNVo(
                1, // accepted
                "连接成功",
                sessionId,
                (double) spawnPos.x,
                (double) spawnPos.y,
                (double) spawnPos.z,
                (float)newPlayer.getYaw(),
                (float)newPlayer.getPitch());

        log.info("发送加入响应: sessionId={}, spawnPos=({}, {}, {})", sessionId, spawnPos.x, spawnPos.y, spawnPos.z);
        handler.sendPacket(response);
    }

    private void handleClientReady(ClientConnectionHandler handler) {
        // 客户端已准备好，执行初始同步
        ServerPlayer player = handler.getPlayer();
        if (worldManager.getWorld(defaultWorldName) == null || player == null) {
            log.warn("无法执行初始同步: world={}, player={}", worldManager.getWorld(defaultWorldName) != null, player != null);
            return;
        }

        log.info("开始为客户端执行初始区块同步，玩家位置: ({}, {}, {})",
                player.getPosition().x, player.getPosition().y, player.getPosition().z);
        performInitialSyncForClient(handler, player);

        // 初始同步完成后，标记玩家已初始化
        handler.setPlayerInitialized(true);
        log.info("玩家初始化完成，可以开始接收位置更新");
    }

    /**
     * 在指定位置周围生成区块（仅生成，不发送给客户端）
     *
     * @param centerPosition 中心位置
     * @param radius         生成半径（区块数）
     */
    private void generateChunksAround(Vector3f centerPosition, int radius) {
        if (worldManager.getWorld(defaultWorldName) == null) {
            return;
        }

        int centerChunkX = (int) Math
                .floor(centerPosition.x / ServerChunkOld.CHUNK_SIZE);
        int centerChunkZ = (int) Math
                .floor(centerPosition.z / ServerChunkOld.CHUNK_SIZE);

        log.info("开始生成出生点周围的区块: centerChunk=({}, {}), radius={}", centerChunkX, centerChunkZ, radius);

        for (int x = centerChunkX - radius; x <= centerChunkX + radius; x++) {
            for (int z = centerChunkZ - radius; z <= centerChunkZ + radius; z++) {
                ServerChunkOld chunk = worldManager.getWorld(defaultWorldName).getChunk(x, z);
                if (chunk == null) {
                    worldManager.getWorld(defaultWorldName).generateChunkSynchronously(x, z);
                }
            }
        }

        log.info("区块生成完成");
    }

    /**
     * 查找指定坐标的安全出生点Y坐标
     * 从上到下扫描，找到第一个非空气方块，返回其上方的Y坐标
     *
     * @param worldX 世界X坐标
     * @param worldZ 世界Z坐标
     * @return 安全的Y坐标，如果找不到则返回64
     */
    private int findSafeSpawnY(int worldX, int worldZ) {
        if (worldManager.getWorld(defaultWorldName) == null) {
            return 64;
        }

        // 从较高的位置开始向下扫描（从Y=200开始，避免扫描整个高度）
        for (int y = 200; y >= 0; y--) {
            int blockState = worldManager.getWorld(defaultWorldName).getBlockState(worldX, y, worldZ);
            // 如果找到非空气方块（stateId != 0），返回其上方的Y坐标
            if (blockState != 0) {
                int safeY = y + 1;
                log.debug("找到安全出生点: ({}, {}, {})", worldX, safeY, worldZ);
                return safeY;
            }
        }

        // 如果找不到，返回默认值
        log.warn("未找到安全出生点，使用默认Y=64: ({}, {})", worldX, worldZ);
        return 64;
    }

    private void performInitialSyncForClient(ClientConnectionHandler handler, ServerPlayer player) {
        if (worldManager.getWorld(defaultWorldName) == null || player == null) {
            return;
        }

        int playerChunkX = (int) Math
                .floor(player.getPosition().x / ServerChunkOld.CHUNK_SIZE);
        int playerChunkZ = (int) Math
                .floor(player.getPosition().z / ServerChunkOld.CHUNK_SIZE);

        for (int x = playerChunkX - INITIAL_RENDER_DISTANCE; x <= playerChunkX + INITIAL_RENDER_DISTANCE; x++) {
            for (int z = playerChunkZ - INITIAL_RENDER_DISTANCE; z <= playerChunkZ + INITIAL_RENDER_DISTANCE; z++) {
                ServerChunkOld chunk = worldManager.getWorld(defaultWorldName).getChunk(x, z);
                if (chunk == null) {
                    worldManager.getWorld(defaultWorldName).generateChunkSynchronously(x, z);
                    chunk = worldManager.getWorld(defaultWorldName).getChunk(x, z);
                }
                if (chunk != null) {
                    // 将区块数据转换为byte[]
                    int[][][] blockStates = new int[ServerChunkOld.CHUNK_SIZE][ServerChunkOld.CHUNK_HEIGHT][ServerChunkOld.CHUNK_SIZE];
                    for (int localX = 0; localX < ServerChunkOld.CHUNK_SIZE; localX++) {
                        for (int y = 0; y < ServerChunkOld.CHUNK_HEIGHT; y++) {
                            for (int localZ = 0; localZ < ServerChunkOld.CHUNK_SIZE; localZ++) {
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
    public java.util.List<ClientConnectionHandler> getConnectedClients() {
        return new java.util.ArrayList<>(connectedClients);
    }

    /**
     * 将区块数据序列化为byte[]
     */
    private byte[] serializeChunkData(int[][][] blockStates) {
        int size = ServerChunkOld.CHUNK_SIZE * ServerChunkOld.CHUNK_HEIGHT * ServerChunkOld.CHUNK_SIZE;
        byte[] data = new byte[size * 4];
        int index = 0;

        for (int x = 0; x < ServerChunkOld.CHUNK_SIZE; x++) {
            for (int y = 0; y < ServerChunkOld.CHUNK_HEIGHT; y++) {
                for (int z = 0; z < ServerChunkOld.CHUNK_SIZE; z++) {
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

        //关闭网络监听器
        stopNetworkListener();

        //关闭所有客户端连接
        for (ClientConnectionHandler handler : connectedClients) {
            handler.close();
        }

        //停止所有世界的运行并将它们写入归档
        worldManager.shutdown();

        //断开归档索引数据库连接
        archiveManager.disconnectArchiveIndex();
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

}