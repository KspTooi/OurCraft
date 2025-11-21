package com.ksptool.ourcraft.server;

import com.ksptool.ourcraft.server.entity.ServerPlayer;
import com.ksptool.ourcraft.server.network.ClientConnectionHandler;
import com.ksptool.ourcraft.server.world.ServerChunk;
import com.ksptool.ourcraft.server.world.ServerWorld;
import com.ksptool.ourcraft.server.world.save.SaveManager;
import com.ksptool.ourcraft.sharedcore.events.*;
import com.ksptool.ourcraft.sharedcore.network.packets.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3f;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 服务端运行实例，负责逻辑更新（Tick-based loop）
 * 负责网络连接、玩家管理、世界管理等
 */
@Slf4j
@Getter
public class OurCraftServerInstance {

    private static final int INITIAL_RENDER_DISTANCE = 8;
    private static final int NETWORK_PORT = 25564;

    private ServerWorld world;
    private boolean running;
    private Thread serverThread;
    private Thread networkListenerThread;
    private ServerSocket serverSocket;
    private final CopyOnWriteArrayList<ClientConnectionHandler> connectedClients = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<Integer, ClientConnectionHandler> sessionIdToHandler = new ConcurrentHashMap<>();

    public OurCraftServerInstance() {
        this.running = false;
    }

    public void init(ServerWorld world) {
        this.world = world;
    }

    public void start() {
        if (running) {
            return;
        }

        running = true;
        serverThread = new Thread(this::run, "GameServer");
        serverThread.start();

        startNetworkListener();
    }

    private void processEvents() {
        // MP模式下，输入事件通过网络数据包直接处理
        java.util.List<GameEvent> events = EventQueue.getInstance().pollAllC2S();

        if (!events.isEmpty()) {
            log.debug("GameServer: 处理{}个事件（SP模式）", events.size());
        }

        // SP模式下，处理第一个Player实体的事件
        if (!events.isEmpty() && world != null) {
            java.util.List<com.ksptool.ourcraft.server.entity.ServerEntity> entities = world.getEntities();
            ServerPlayer singlePlayer = null;
            for (com.ksptool.ourcraft.server.entity.ServerEntity entity : entities) {
                if (entity instanceof ServerPlayer) {
                    singlePlayer = (ServerPlayer) entity;
                    break;
                }
            }

            if (singlePlayer != null) {
                for (GameEvent event : events) {
                    if (event instanceof PlayerInputEvent) {
                        singlePlayer.applyInput((PlayerInputEvent) event);
                    } else if (event instanceof PlayerHotbarSwitchEvent) {
                        singlePlayer.getInventory().scrollSelection(((PlayerHotbarSwitchEvent) event).getSlotDelta());
                        singlePlayer.markDirty(true);
                    } else if (event instanceof PlayerActionEvent) {
                        PlayerActionEvent actionEvent = (PlayerActionEvent) event;
                        if (actionEvent.getAction() == PlayerAction.ATTACK) {
                            singlePlayer.handleBlockBreak();
                        } else if (actionEvent.getAction() == PlayerAction.USE) {
                            singlePlayer.handleBlockPlace();
                        }
                    } else if (event instanceof PlayerCameraInputEvent) {
                        PlayerCameraInputEvent cameraEvent = (PlayerCameraInputEvent) event;
                        singlePlayer.updateCameraOrientation(cameraEvent.getDeltaYaw(), cameraEvent.getDeltaPitch());
                    }
                }
            }
        }
    }

    /**
     * 处理方块更新事件，发送网络数据包
     */
    private void processBlockUpdates() {
        if (connectedClients.isEmpty()) {
            return;
        }

        // 从EventQueue中获取方块更新事件（这些事件是ServerWorld发送的）
        java.util.List<GameEvent> s2cEvents = EventQueue.getInstance().pollAllS2C();
        for (GameEvent event : s2cEvents) {
            if (event instanceof com.ksptool.ourcraft.sharedcore.events.BlockUpdateEvent) {
                com.ksptool.ourcraft.sharedcore.events.BlockUpdateEvent blockEvent =
                        (com.ksptool.ourcraft.sharedcore.events.BlockUpdateEvent) event;
                ServerSyncBlockUpdateNVo packet = new ServerSyncBlockUpdateNVo(
                        blockEvent.getX(),
                        blockEvent.getY(),
                        blockEvent.getZ(),
                        blockEvent.getNewStateId()
                );

                // 创建客户端列表的副本，避免并发修改异常
                java.util.List<ClientConnectionHandler> clients = new java.util.ArrayList<>(connectedClients);
                for (ClientConnectionHandler handler : clients) {
                    if (handler.isConnected()) {
                        handler.sendPacket(packet);
                    }
                }
            }
        }
    }


    public void stop() {
        running = false;

        // 关闭网络监听器
        stopNetworkListener();

        // 关闭所有客户端连接
        for (ClientConnectionHandler handler : connectedClients) {
            handler.close();
        }
        connectedClients.clear();
        sessionIdToHandler.clear();
    }

    /**
     * 当客户端断开连接时调用，移除对应的Player实体
     */
    public void onClientDisconnected(ClientConnectionHandler handler) {
        ServerPlayer player = handler.getPlayer();
        if (player != null && world != null) {
            String saveName = world.getSaveName();
            String worldName = world.getWorldName();
            if (saveName != null && worldName != null) {
                SaveManager.getInstance().savePlayer(saveName, player.getUniqueId(), player);
                log.info("已保存断开连接的玩家数据: UUID={}", player.getUniqueId());
            }
            log.info("移除断开连接的玩家实体");
            world.removeEntity(player);
            handler.setPlayer(null);
        }

        if (serverThread != null) {
            try {
                serverThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
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

                while (running) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        log.info("新的客户端连接: {}", clientSocket.getRemoteSocketAddress());

                        ClientConnectionHandler handler = new ClientConnectionHandler(clientSocket, this);
                        connectedClients.add(handler);
                        Thread.ofVirtual().start(handler);
                    } catch (IOException e) {
                        if (running) {
                            log.warn("接受客户端连接时发生错误: {}", e.getMessage());
                        }
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
                    player.getYaw(),
                    player.getPitch()
            );
            // 立即将纠正数据包发回给该客户端
            handler.sendPacket(correctionPacket);
            log.info("向客户端发送位置纠正包，强制同步到服务器位置");
            return; // 忽略这个错误的数据包，不更新服务器位置
        }

        player.getPosition().set(newPosition);
        player.setYaw(packet.yaw());
        player.setPitch(packet.pitch());
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

        // 创建 PlayerInputEvent 并应用输入
        PlayerInputEvent inputEvent = new PlayerInputEvent(
                packet.w(),
                packet.s(),
                packet.a(),
                packet.d(),
                packet.space()
        );
        player.applyInput(inputEvent);

        // 更新相机朝向
        float currentYaw = player.getYaw();
        float currentPitch = player.getPitch();
        float deltaYaw = packet.yaw() - currentYaw;
        float deltaPitch = packet.pitch() - currentPitch;

        // 处理角度环绕（-180到180度）
        if (deltaYaw > 180.0f) {
            deltaYaw -= 360.0f;
        } else if (deltaYaw < -180.0f) {
            deltaYaw += 360.0f;
        }

        player.updateCameraOrientation(deltaYaw, deltaPitch);
        player.markDirty(true);
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

        if (world == null) {
            log.error("世界未初始化，无法接受玩家加入");
            RequestJoinServerNVo response = new RequestJoinServerNVo(
                    0, // rejected
                    "服务器世界未初始化",
                    null, null, null, null, null, null
            );
            handler.sendPacket(response);
            return;
        }

        String saveName = world.getSaveName();
        UUID playerUUID = SaveManager.getInstance().getOrCreatePlayerUUID(saveName, packet.playerName());
        ServerPlayer newPlayer = new ServerPlayer(world, playerUUID);

        com.ksptool.ourcraft.server.world.save.PlayerIndex playerIndex = SaveManager.getInstance().loadPlayer(saveName, playerUUID);
        Vector3f spawnPos;

        if (playerIndex != null) {
            newPlayer.loadFromPlayerIndex(playerIndex);
            spawnPos = new Vector3f(playerIndex.posX, playerIndex.posY, playerIndex.posZ);
            log.info("加载了玩家 '{}' 的数据, UUID: {}, 位置: ({}, {}, {})",
                    packet.playerName(), playerUUID, spawnPos.x, spawnPos.y, spawnPos.z);

            int playerChunkX = (int) Math.floor(spawnPos.x / ServerChunk.CHUNK_SIZE);
            int playerChunkZ = (int) Math.floor(spawnPos.z / ServerChunk.CHUNK_SIZE);
            generateChunksAround(spawnPos, INITIAL_RENDER_DISTANCE);
        } else {
            Vector3f initialSpawnPos = new Vector3f(0, 64, 0);
            log.info("开始生成出生点周围的区块，确保地面存在");
            generateChunksAround(initialSpawnPos, INITIAL_RENDER_DISTANCE);
            int safeSpawnY = findSafeSpawnY((int) initialSpawnPos.x, (int) initialSpawnPos.z);
            spawnPos = new Vector3f(initialSpawnPos.x, safeSpawnY, initialSpawnPos.z);
            log.info("为新玩家 '{}' 计算得到安全出生点: ({}, {}, {})", packet.playerName(), spawnPos.x, spawnPos.y, spawnPos.z);
            newPlayer.getPosition().set(spawnPos);
            newPlayer.setYaw(0.0f);
            newPlayer.setPitch(0.0f);
        }

        world.addEntity(newPlayer);
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
                newPlayer.getYaw(),
                newPlayer.getPitch()
        );

        log.info("发送加入响应: sessionId={}, spawnPos=({}, {}, {})", sessionId, spawnPos.x, spawnPos.y, spawnPos.z);
        handler.sendPacket(response);
    }

    private void handleClientReady(ClientConnectionHandler handler) {
        // 客户端已准备好，执行初始同步
        ServerPlayer player = handler.getPlayer();
        if (world == null || player == null) {
            log.warn("无法执行初始同步: world={}, player={}", world != null, player != null);
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
        if (world == null) {
            return;
        }

        int centerChunkX = (int) Math.floor(centerPosition.x / com.ksptool.ourcraft.server.world.ServerChunk.CHUNK_SIZE);
        int centerChunkZ = (int) Math.floor(centerPosition.z / com.ksptool.ourcraft.server.world.ServerChunk.CHUNK_SIZE);

        log.info("开始生成出生点周围的区块: centerChunk=({}, {}), radius={}", centerChunkX, centerChunkZ, radius);

        for (int x = centerChunkX - radius; x <= centerChunkX + radius; x++) {
            for (int z = centerChunkZ - radius; z <= centerChunkZ + radius; z++) {
                com.ksptool.ourcraft.server.world.ServerChunk chunk = world.getChunk(x, z);
                if (chunk == null) {
                    world.generateChunkSynchronously(x, z);
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
        if (world == null) {
            return 64;
        }

        // 从较高的位置开始向下扫描（从Y=200开始，避免扫描整个高度）
        for (int y = 200; y >= 0; y--) {
            int blockState = world.getBlockState(worldX, y, worldZ);
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
        if (world == null || player == null) {
            return;
        }

        int playerChunkX = (int) Math.floor(player.getPosition().x / com.ksptool.ourcraft.server.world.ServerChunk.CHUNK_SIZE);
        int playerChunkZ = (int) Math.floor(player.getPosition().z / com.ksptool.ourcraft.server.world.ServerChunk.CHUNK_SIZE);

        for (int x = playerChunkX - INITIAL_RENDER_DISTANCE; x <= playerChunkX + INITIAL_RENDER_DISTANCE; x++) {
            for (int z = playerChunkZ - INITIAL_RENDER_DISTANCE; z <= playerChunkZ + INITIAL_RENDER_DISTANCE; z++) {
                com.ksptool.ourcraft.server.world.ServerChunk chunk = world.getChunk(x, z);
                if (chunk == null) {
                    world.generateChunkSynchronously(x, z);
                    chunk = world.getChunk(x, z);
                }
                if (chunk != null) {
                    // 将区块数据转换为byte[]
                    int[][][] blockStates = new int[com.ksptool.ourcraft.server.world.ServerChunk.CHUNK_SIZE][com.ksptool.ourcraft.server.world.ServerChunk.CHUNK_HEIGHT][com.ksptool.ourcraft.server.world.ServerChunk.CHUNK_SIZE];
                    for (int localX = 0; localX < com.ksptool.ourcraft.server.world.ServerChunk.CHUNK_SIZE; localX++) {
                        for (int y = 0; y < com.ksptool.ourcraft.server.world.ServerChunk.CHUNK_HEIGHT; y++) {
                            for (int localZ = 0; localZ < com.ksptool.ourcraft.server.world.ServerChunk.CHUNK_SIZE; localZ++) {
                                blockStates[localX][y][localZ] = chunk.getBlockState(localX, y, localZ);
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
     * 查找玩家对应的连接处理器
     */
    private ClientConnectionHandler findHandlerForPlayer(ServerPlayer player) {
        for (ClientConnectionHandler handler : connectedClients) {
            if (handler.getPlayer() == player) {
                return handler;
            }
        }
        return null;
    }

    /**
     * 执行一次游戏逻辑滴答（tick）- 纯模拟，不发送网络包
     *
     * @param tickDelta 固定的时间增量（例如 0.05 秒，对应 20 TPS）
     */
    private void tick(float tickDelta) {
        if (world == null) {
            log.warn("GameServer: world为null，跳过本次更新");
            return;
        }

        // 1. 处理客户端发来的事件
        processEvents();

        // 2. 计算玩家位置，用于区块管理
        Vector3f centerPosition = new Vector3f(0, 64, 0);
        if (!connectedClients.isEmpty()) {
            // 创建客户端列表的副本，避免并发修改异常
            java.util.List<ClientConnectionHandler> clients = new java.util.ArrayList<>(connectedClients);
            if (!clients.isEmpty()) {
                // 如果有连接的客户端，使用第一个玩家的位置作为中心
                ClientConnectionHandler firstHandler = clients.get(0);
                if (firstHandler.getPlayer() != null) {
                    centerPosition.set(firstHandler.getPlayer().getPosition());
                }
            }
        }

        // 3. 更新世界状态和所有实体的物理
        world.update(tickDelta, centerPosition, () -> {
            // 更新所有实体（创建副本避免并发修改异常）
            java.util.List<com.ksptool.ourcraft.server.entity.ServerEntity> entities = new java.util.ArrayList<>(world.getEntities());
            for (com.ksptool.ourcraft.server.entity.ServerEntity entity : entities) {
                // 如果是玩家实体，检查是否已初始化完成
                if (entity instanceof ServerPlayer) {
                    ServerPlayer player = (ServerPlayer) entity;
                    // 查找对应的连接处理器
                    ClientConnectionHandler handler = findHandlerForPlayer(player);
                    // 如果玩家尚未初始化完成，跳过物理更新，避免在初始化期间下落
                    if (handler != null && !handler.isPlayerInitialized()) {
                        continue;
                    }
                }
                entity.update(tickDelta);
            }
        });
    }

    /**
     * 发送网络更新（将当前游戏状态同步给所有客户端）
     * 此方法应在所有 tick 完成后调用，确保只发送最终状态
     */
    private void sendNetworkUpdates() {
        if (world == null) {
            return;
        }

        // 1. 发送方块更新
        processBlockUpdates();

        // 2. 发送区块加载/卸载信息
        updateDynamicViewport();

        // 3. 发送玩家和实体的位置信息
        sendPlayerUpdate();
    }

    private void run() {
        log.info("GameServer主循环已启动");

        if (world == null || world.getTemplate() == null) {
            log.error("GameServer 无法启动: world 或 world template 为 null");
            running = false;
            return;
        }

        final double tickRate = world.getTemplate().getTicksPerSecond();
        final double tickTime = 1.0 / tickRate;

        double lastTime = System.nanoTime() / 1_000_000_000.0;
        double accumulator = 0.0;

        while (running) {
            double now = System.nanoTime() / 1_000_000_000.0;
            double deltaSeconds = now - lastTime;
            lastTime = now;

            accumulator += deltaSeconds;

            boolean ticked = false;
            // 运行所有需要追赶的 tick（纯模拟，不发送网络包）
            while (accumulator >= tickTime) {
                tick((float) tickTime);
                accumulator -= tickTime;
                ticked = true;
            }

            // 如果本轮循环至少执行过一次 tick
            if (ticked) {
                // 则在所有模拟完成后，发送一次最终的网络状态
                sendNetworkUpdates();
            }

            // 添加短暂休眠以防止CPU空转
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false;
            }
        }

        log.info("GameServer主循环已停止");
    }

    private void sendPlayerUpdate() {
        // 优先通过网络连接发送，如果没有连接则使用EventQueue（兼容单人游戏）
        if (!connectedClients.isEmpty()) {
            // 创建客户端列表的副本，避免并发修改异常
            java.util.List<ClientConnectionHandler> clients = new java.util.ArrayList<>(connectedClients);

            // 为每个连接的客户端发送其对应的玩家状态
            for (ClientConnectionHandler handler : clients) {
                if (!handler.isConnected()) {
                    continue;
                }

                ServerPlayer player = handler.getPlayer();
                if (player == null) {
                    continue;
                }

                // 只有在玩家初始化完成后才发送位置和状态更新
                // 这样可以避免在客户端还在加载世界数据时发送大量位置包
                if (!handler.isPlayerInitialized()) {
                    continue;
                }

                // 发送该玩家的位置和朝向
                ServerSyncEntityPositionAndRotationNVo positionPacket = new ServerSyncEntityPositionAndRotationNVo(
                        0, // entityId 0 表示玩家自己
                        player.getPosition().x,
                        player.getPosition().y,
                        player.getPosition().z,
                        player.getYaw(),
                        player.getPitch()
                );

                // 发送该玩家的状态
                ServerSyncPlayerStateNVo statePacket = new ServerSyncPlayerStateNVo(
                        player.getHealth(),
                        20, // foodLevel TODO: 从player获取
                        0, // experienceLevel TODO: 从player获取
                        0.0f // experienceProgress TODO: 从player获取
                );

                handler.sendPacket(positionPacket);
                handler.sendPacket(statePacket);
            }

            // 广播所有其他实体的位置（包括其他玩家）
            broadcastEntityPositions();
        } else {
            // 单人游戏模式，使用EventQueue
            // 注意：单人模式下可能没有玩家实体，需要检查
            if (world != null) {
                java.util.List<com.ksptool.ourcraft.server.entity.ServerEntity> entities = new java.util.ArrayList<>(world.getEntities());
                for (com.ksptool.ourcraft.server.entity.ServerEntity entity : entities) {
                    if (entity instanceof ServerPlayer) {
                        ServerPlayer player = (ServerPlayer) entity;
                        EventQueue eventQueue = EventQueue.getInstance();
                        eventQueue.offerS2C(new PlayerUpdateEvent(
                                player.getPosition(),
                                player.getPreviousPosition(),
                                player.getYaw(),
                                player.getPitch(),
                                player.getPreviousYaw(),
                                player.getPreviousPitch(),
                                player.getInventory().getSelectedSlot()
                        ));
                        break;
                    }
                }
            }
        }
    }

    /**
     * 广播所有实体的位置和朝向给所有客户端
     */
    private void broadcastEntityPositions() {
        if (world == null || connectedClients.isEmpty()) {
            return;
        }

        // 创建实体列表的副本，避免并发修改异常
        java.util.List<com.ksptool.ourcraft.server.entity.ServerEntity> entities = new java.util.ArrayList<>(world.getEntities());
        for (com.ksptool.ourcraft.server.entity.ServerEntity entity : entities) {
            if (entity instanceof ServerPlayer) {
                ServerPlayer player = (ServerPlayer) entity;

                // 获取玩家的实体ID（用于网络同步）
                int entityId = getEntityIdForPlayer(player);
                // 如果找不到实体ID（返回0表示未找到，因为sessionId从1开始），则跳过广播
                // 这可以避免在玩家初始化过程中发送错误的位置数据包
                if (entityId == 0) {
                    continue;
                }

                // 为每个客户端发送实体位置（除了该实体对应的客户端，因为它已经收到了自己的位置）
                ServerSyncEntityPositionAndRotationNVo positionPacket = new ServerSyncEntityPositionAndRotationNVo(
                        entityId, // 使用正确的实体ID
                        player.getPosition().x,
                        player.getPosition().y,
                        player.getPosition().z,
                        player.getYaw(),
                        player.getPitch()
                );

                // 创建客户端列表的副本，避免并发修改异常
                java.util.List<ClientConnectionHandler> clients = new java.util.ArrayList<>(connectedClients);
                // 广播给所有客户端（除了该玩家自己的客户端）
                for (ClientConnectionHandler handler : clients) {
                    if (!handler.isConnected()) {
                        continue;
                    }
                    // 如果这个handler对应的玩家就是当前实体，跳过（因为已经发送过了）
                    if (handler.getPlayer() == player) {
                        continue;
                    }
                    handler.sendPacket(positionPacket);
                }
            }
        }
    }

    /**
     * 获取玩家的实体ID（用于网络同步）
     * 简单实现：使用sessionId
     */
    private int getEntityIdForPlayer(ServerPlayer player) {
        for (java.util.Map.Entry<Integer, ClientConnectionHandler> entry : sessionIdToHandler.entrySet()) {
            if (entry.getValue().getPlayer() == player) {
                return entry.getKey();
            }
        }
        return 0;
    }

    private void updateDynamicViewport() {
        if (world == null) {
            return;
        }

        // 创建客户端列表的副本，避免并发修改异常
        java.util.List<ClientConnectionHandler> clients = new java.util.ArrayList<>(connectedClients);

        // 为每个连接的玩家更新视口
        for (ClientConnectionHandler handler : clients) {
            if (!handler.isConnected()) {
                continue;
            }

            ServerPlayer player = handler.getPlayer();
            if (player == null) {
                continue;
            }

            updatePlayerViewport(handler, player);
        }
    }

    private void updatePlayerViewport(ClientConnectionHandler handler, ServerPlayer player) {
        if (world == null || player == null) {
            return;
        }

        int playerChunkX = (int) Math.floor(player.getPosition().x / com.ksptool.ourcraft.server.world.ServerChunk.CHUNK_SIZE);
        int playerChunkZ = (int) Math.floor(player.getPosition().z / com.ksptool.ourcraft.server.world.ServerChunk.CHUNK_SIZE);

        // 获取或初始化该玩家的上次区块位置
        Integer lastChunkX = handler.getLastChunkX();
        Integer lastChunkZ = handler.getLastChunkZ();

        if (lastChunkX == null || lastChunkZ == null) {
            handler.setLastChunkX(playerChunkX);
            handler.setLastChunkZ(playerChunkZ);
            return;
        }

        if (playerChunkX == lastChunkX && playerChunkZ == lastChunkZ) {
            return;
        }

        java.util.Set<java.util.AbstractMap.SimpleEntry<Integer, Integer>> oldViewport = new java.util.HashSet<>();
        java.util.Set<java.util.AbstractMap.SimpleEntry<Integer, Integer>> newViewport = new java.util.HashSet<>();

        for (int x = lastChunkX - INITIAL_RENDER_DISTANCE; x <= lastChunkX + INITIAL_RENDER_DISTANCE; x++) {
            for (int z = lastChunkZ - INITIAL_RENDER_DISTANCE; z <= lastChunkZ + INITIAL_RENDER_DISTANCE; z++) {
                oldViewport.add(new java.util.AbstractMap.SimpleEntry<>(x, z));
            }
        }

        for (int x = playerChunkX - INITIAL_RENDER_DISTANCE; x <= playerChunkX + INITIAL_RENDER_DISTANCE; x++) {
            for (int z = playerChunkZ - INITIAL_RENDER_DISTANCE; z <= playerChunkZ + INITIAL_RENDER_DISTANCE; z++) {
                newViewport.add(new java.util.AbstractMap.SimpleEntry<>(x, z));
            }
        }

        // 发送新加载的区块
        for (java.util.AbstractMap.SimpleEntry<Integer, Integer> chunkPos : newViewport) {
            if (!oldViewport.contains(chunkPos)) {
                int x = chunkPos.getKey();
                int z = chunkPos.getValue();
                com.ksptool.ourcraft.server.world.ServerChunk chunk = world.getChunk(x, z);
                if (chunk == null) {
                    world.generateChunkSynchronously(x, z);
                    chunk = world.getChunk(x, z);
                }
                if (chunk != null) {
                    int[][][] blockStates = new int[com.ksptool.ourcraft.server.world.ServerChunk.CHUNK_SIZE][com.ksptool.ourcraft.server.world.ServerChunk.CHUNK_HEIGHT][com.ksptool.ourcraft.server.world.ServerChunk.CHUNK_SIZE];
                    for (int localX = 0; localX < com.ksptool.ourcraft.server.world.ServerChunk.CHUNK_SIZE; localX++) {
                        for (int y = 0; y < com.ksptool.ourcraft.server.world.ServerChunk.CHUNK_HEIGHT; y++) {
                            for (int localZ = 0; localZ < com.ksptool.ourcraft.server.world.ServerChunk.CHUNK_SIZE; localZ++) {
                                blockStates[localX][y][localZ] = chunk.getBlockState(localX, y, localZ);
                            }
                        }
                    }

                    byte[] blockData = serializeChunkData(blockStates);
                    ServerSyncChunkDataNVo chunkPacket = new ServerSyncChunkDataNVo(x, 0, z, blockData);
                    handler.sendPacket(chunkPacket);
                }
            }
        }

        // 发送卸载的区块
        for (java.util.AbstractMap.SimpleEntry<Integer, Integer> chunkPos : oldViewport) {
            if (!newViewport.contains(chunkPos)) {
                int x = chunkPos.getKey();
                int z = chunkPos.getValue();

                ServerSyncUnloadChunkNVo unloadPacket = new ServerSyncUnloadChunkNVo(x, 0, z);
                handler.sendPacket(unloadPacket);
            }
        }

        // 更新该玩家的上次区块位置
        handler.setLastChunkX(playerChunkX);
        handler.setLastChunkZ(playerChunkZ);
    }

    /**
     * 将区块数据序列化为byte[]
     */
    private byte[] serializeChunkData(int[][][] blockStates) {
        int size = ServerChunk.CHUNK_SIZE * ServerChunk.CHUNK_HEIGHT * ServerChunk.CHUNK_SIZE;
        byte[] data = new byte[size * 4];
        int index = 0;

        for (int x = 0; x < ServerChunk.CHUNK_SIZE; x++) {
            for (int y = 0; y < ServerChunk.CHUNK_HEIGHT; y++) {
                for (int z = 0; z < ServerChunk.CHUNK_SIZE; z++) {
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

    public void cleanup() {
        stop();
        if (world != null) {
            String saveName = world.getSaveName();
            String worldName = world.getWorldName();
            if (saveName != null && worldName != null) {
                java.util.List<ClientConnectionHandler> clients = new java.util.ArrayList<>(connectedClients);
                for (ClientConnectionHandler handler : clients) {
                    ServerPlayer player = handler.getPlayer();
                    if (player != null) {
                        SaveManager.getInstance().savePlayer(saveName, player.getUniqueId(), player);
                        log.info("已保存玩家数据: UUID={}", player.getUniqueId());
                    }
                }
            }
            world.saveAllDirtyData();
            world.cleanup();
        }
    }
}

