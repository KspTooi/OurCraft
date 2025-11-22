package com.ksptool.ourcraft.server;

import com.ksptool.ourcraft.server.archive.ArchiveManager;
import com.ksptool.ourcraft.server.entity.ServerPlayer;
import com.ksptool.ourcraft.server.manager.ServerWorldManager;
import com.ksptool.ourcraft.server.network.ClientConnectionHandler;
import com.ksptool.ourcraft.server.world.ServerChunk;
import com.ksptool.ourcraft.server.world.save.SaveManager;
import com.ksptool.ourcraft.sharedcore.events.PlayerInputEvent;
import com.ksptool.ourcraft.sharedcore.network.packets.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
public class OurCraftServer {

    private static final int INITIAL_RENDER_DISTANCE = 8;
    private static final int NETWORK_PORT = 25564;

    private final ServerWorldManager worldManager;

    private final String worldName = "earth_like";

    private Thread networkListenerThread;

    private ServerSocket serverSocket;

    private final CopyOnWriteArrayList<ClientConnectionHandler> connectedClients = new CopyOnWriteArrayList<>();

    private final ConcurrentHashMap<Integer, ClientConnectionHandler> sessionIdToHandler = new ConcurrentHashMap<>();

    //归档管理器
    private final ArchiveManager archiveManager;

    public OurCraftServer(String saveName) {

        var _saveName = saveName;

        if (StringUtils.isBlank(_saveName)) {
            _saveName = "our_craft";
            log.warn("使用默认的归档: {}", _saveName);
        }

        //初始化归档管理器
        this.archiveManager = new ArchiveManager();

        //创建世界管理器
        this.worldManager = new ServerWorldManager(this, _saveName);
        
        //读取现有归档
        if(!archiveManager.existsArchive(_saveName)){
            archiveManager.createArchive(_saveName);
        }

        //打开归档索引数据库连接
        archiveManager.connectArchiveIndex();
    }

    public void start() {

        // 加载世界
        worldManager.loadWorld(worldName);

        // 启动世界
        worldManager.runWorld(worldName);

        // 启动网络监听器
        startNetworkListener();
    }

    public void stop() {

        // 关闭网络监听器
        stopNetworkListener();

        // 关闭所有客户端连接
        for (ClientConnectionHandler handler : connectedClients) {
            handler.close();
        }

        // 停止世界
        worldManager.stopWorld(worldName);

        // 卸载并保存世界
        worldManager.unloadWorldAndSave(worldName);
    }

    /**
     * 当客户端断开连接时调用，移除对应的Player实体
     */
    public void onClientDisconnected(ClientConnectionHandler handler) {
        ServerPlayer player = handler.getPlayer();
        if (player != null) {
            String saveName = worldManager.getWorld(worldName).getSaveName();
            if (saveName != null && worldName != null) {
                SaveManager.getInstance().savePlayer(saveName, player.getUniqueId(), player);
                log.info("已保存断开连接的玩家数据: UUID={}", player.getUniqueId());
            }
            log.info("移除断开连接的玩家实体");

            // 委托世界管理器移除玩家实体
            worldManager.getWorld(worldName).removeEntity(player);
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
                    player.getYaw(),
                    player.getPitch());
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

        if (worldManager.getWorld(worldName) == null) {
            log.error("世界未初始化，无法接受玩家加入");
            RequestJoinServerNVo response = new RequestJoinServerNVo(
                    0, // rejected
                    "服务器世界未初始化",
                    null, null, null, null, null, null);
            handler.sendPacket(response);
            return;
        }

        String saveName = worldManager.getWorld(worldName).getSaveName();
        UUID playerUUID = SaveManager.getInstance().getOrCreatePlayerUUID(saveName, packet.playerName());
        ServerPlayer newPlayer = new ServerPlayer(worldManager.getWorld(worldName), playerUUID);

        com.ksptool.ourcraft.server.world.save.PlayerIndex playerIndex = SaveManager.getInstance().loadPlayer(saveName,
                playerUUID);
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

        worldManager.getWorld(worldName).addEntity(newPlayer);
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
                newPlayer.getPitch());

        log.info("发送加入响应: sessionId={}, spawnPos=({}, {}, {})", sessionId, spawnPos.x, spawnPos.y, spawnPos.z);
        handler.sendPacket(response);
    }

    private void handleClientReady(ClientConnectionHandler handler) {
        // 客户端已准备好，执行初始同步
        ServerPlayer player = handler.getPlayer();
        if (worldManager.getWorld(worldName) == null || player == null) {
            log.warn("无法执行初始同步: world={}, player={}", worldManager.getWorld(worldName) != null, player != null);
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
        if (worldManager.getWorld(worldName) == null) {
            return;
        }

        int centerChunkX = (int) Math
                .floor(centerPosition.x / com.ksptool.ourcraft.server.world.ServerChunk.CHUNK_SIZE);
        int centerChunkZ = (int) Math
                .floor(centerPosition.z / com.ksptool.ourcraft.server.world.ServerChunk.CHUNK_SIZE);

        log.info("开始生成出生点周围的区块: centerChunk=({}, {}), radius={}", centerChunkX, centerChunkZ, radius);

        for (int x = centerChunkX - radius; x <= centerChunkX + radius; x++) {
            for (int z = centerChunkZ - radius; z <= centerChunkZ + radius; z++) {
                com.ksptool.ourcraft.server.world.ServerChunk chunk = worldManager.getWorld(worldName).getChunk(x, z);
                if (chunk == null) {
                    worldManager.getWorld(worldName).generateChunkSynchronously(x, z);
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
        if (worldManager.getWorld(worldName) == null) {
            return 64;
        }

        // 从较高的位置开始向下扫描（从Y=200开始，避免扫描整个高度）
        for (int y = 200; y >= 0; y--) {
            int blockState = worldManager.getWorld(worldName).getBlockState(worldX, y, worldZ);
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
        if (worldManager.getWorld(worldName) == null || player == null) {
            return;
        }

        int playerChunkX = (int) Math
                .floor(player.getPosition().x / com.ksptool.ourcraft.server.world.ServerChunk.CHUNK_SIZE);
        int playerChunkZ = (int) Math
                .floor(player.getPosition().z / com.ksptool.ourcraft.server.world.ServerChunk.CHUNK_SIZE);

        for (int x = playerChunkX - INITIAL_RENDER_DISTANCE; x <= playerChunkX + INITIAL_RENDER_DISTANCE; x++) {
            for (int z = playerChunkZ - INITIAL_RENDER_DISTANCE; z <= playerChunkZ + INITIAL_RENDER_DISTANCE; z++) {
                com.ksptool.ourcraft.server.world.ServerChunk chunk = worldManager.getWorld(worldName).getChunk(x, z);
                if (chunk == null) {
                    worldManager.getWorld(worldName).generateChunkSynchronously(x, z);
                    chunk = worldManager.getWorld(worldName).getChunk(x, z);
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
        if (worldManager.getWorld(worldName) != null) {
            String saveName = worldManager.getWorld(worldName).getSaveName();
            if (saveName != null) {
                java.util.List<ClientConnectionHandler> clients = new java.util.ArrayList<>(connectedClients);
                for (ClientConnectionHandler handler : clients) {
                    ServerPlayer player = handler.getPlayer();
                    if (player != null) {
                        SaveManager.getInstance().savePlayer(saveName, player.getUniqueId(), player);
                        log.info("已保存玩家数据: UUID={}", player.getUniqueId());
                    }
                }
            }
            worldManager.getWorld(worldName).saveAllDirtyData();
            worldManager.getWorld(worldName).cleanup();
        }
    }

}