package com.ksptool.ourcraft.client.network;

import com.ksptool.ourcraft.client.GameClient;
import com.ksptool.ourcraft.client.world.ClientWorld;
import com.ksptool.ourcraft.sharedcore.network.KryoManager;
import com.ksptool.ourcraft.sharedcore.network.packets.*;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 服务端连接管理器，负责管理与服务器的Socket连接和通信
 */
@Slf4j
public class ServerConnection {
    
    private Socket socket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private volatile boolean connected = false;
    private Thread receiveThread;
    private GameClient gameClient;
    private ClientWorld clientWorld;
    private final BlockingQueue<Object> packetQueue = new LinkedBlockingQueue<>();
    
    public ServerConnection(GameClient gameClient) {
        this.gameClient = gameClient;
    }
    
    /**
     * 连接到服务器
     */
    public boolean connect(String host, int port) {
        if (connected) {
            log.warn("已经连接到服务器");
            return false;
        }
        
        try {
            socket = new Socket(host, port);
            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();
            connected = true;
            
            log.info("已连接到服务器: {}:{}", host, port);
            
            // 启动接收线程
            receiveThread = Thread.ofVirtual().start(this::receiveLoop);
            
            return true;
        } catch (IOException e) {
            log.error("连接服务器失败: {}", e.getMessage());
            connected = false;
            return false;
        }
    }
    
    /**
     * 接收数据包的循环
     */
    private void receiveLoop() {
        while (connected && !socket.isClosed()) {
            try {
                Object packet = KryoManager.readObject(inputStream);
                if (packet == null) {
                    log.warn("从服务器读取到null数据包");
                    continue;
                }
                log.debug("收到数据包: {}", packet.getClass().getSimpleName());
                packetQueue.offer(packet);
            } catch (IOException e) {
                if (connected) {
                    log.warn("从服务器读取数据包时发生错误: {}", e.getMessage());
                }
                break;
            } catch (Exception e) {
                log.error("处理服务器数据包时发生错误", e);
                break;
            }
        }
        
        connected = false;
        log.info("与服务器的连接已断开");
    }
    
    /**
     * 处理接收到的数据包
     */
    private void handlePacket(Object packet) {
        if (packet == null) {
            log.warn("收到null数据包，忽略");
            return;
        }
        
        String packetType = packet.getClass().getSimpleName();
        log.info("收到数据包: {}", packetType);
        
        if (packet instanceof ServerSyncChunkDataNVo) {
            handleChunkData((ServerSyncChunkDataNVo) packet);
        } else if (packet instanceof ServerSyncUnloadChunkNVo) {
            handleChunkUnload((ServerSyncUnloadChunkNVo) packet);
        } else if (packet instanceof ServerSyncBlockUpdateNVo) {
            handleBlockUpdate((ServerSyncBlockUpdateNVo) packet);
        } else if (packet instanceof ServerSyncEntityPositionAndRotationNVo) {
            handleEntityPositionAndRotation((ServerSyncEntityPositionAndRotationNVo) packet);
        } else if (packet instanceof ServerSyncPlayerStateNVo) {
            handlePlayerState((ServerSyncPlayerStateNVo) packet);
        } else if (packet instanceof ServerSyncWorldTimeNVo) {
            handleWorldTime((ServerSyncWorldTimeNVo) packet);
        } else if (packet instanceof RequestJoinServerNVo) {
            handleJoinServerResponse((RequestJoinServerNVo) packet);
        } else if (packet instanceof ServerDisconnectNVo) {
            handleDisconnect((ServerDisconnectNVo) packet);
        } else if (packet instanceof ServerKeepAliveNPkg) {
            // 心跳包，可以在这里更新最后心跳时间
        } else {
            log.warn("收到未知类型的数据包: {}", packet.getClass().getName());
        }
    }
    
    private void handleChunkData(ServerSyncChunkDataNVo packet) {
        if (clientWorld == null) {
            log.warn("收到区块数据但ClientWorld为null: ({}, {})", packet.chunkX(), packet.chunkZ());
            return;
        }
        
        int chunkX = packet.chunkX();
        int chunkZ = packet.chunkZ();
        byte[] blockData = packet.blockData();
        
        log.info("处理区块数据: ({}, {}), 数据长度: {}", chunkX, chunkZ, blockData != null ? blockData.length : 0);
        
        int[][][] blockStates = deserializeChunkData(blockData);
        if (blockStates == null) {
            log.warn("区块数据反序列化失败: ({}, {})", chunkX, chunkZ);
            return;
        }
        
        com.ksptool.ourcraft.client.world.ClientChunk clientChunk = clientWorld.getChunk(chunkX, chunkZ);
        if (clientChunk == null) {
            log.info("创建新客户端区块: ({}, {})", chunkX, chunkZ);
            clientChunk = new com.ksptool.ourcraft.client.world.ClientChunk(chunkX, chunkZ);
            clientWorld.putChunk(chunkX, chunkZ, clientChunk);
        }
        
        clientChunk.setBlockStates(blockStates);
        log.info("区块数据已设置: ({}, {}), 需要网格更新: {}", chunkX, chunkZ, clientChunk.needsMeshUpdate());
        
        // 确保区块被标记为需要网格更新，并添加到dirtyChunks列表
        clientWorld.markChunkForMeshUpdate(chunkX, chunkZ);
        log.info("区块已标记为需要网格更新: ({}, {})", chunkX, chunkZ);
    }
    
    /**
     * 将区块数据从byte[]反序列化为int[][][]
     * 与服务端的serializeChunkData方法对应
     */
    private int[][][] deserializeChunkData(byte[] data) {
        int size = com.ksptool.ourcraft.client.world.ClientChunk.CHUNK_SIZE;
        int height = com.ksptool.ourcraft.client.world.ClientChunk.CHUNK_HEIGHT;
        int expectedSize = size * height * size * 4;
        
        if (data == null || data.length != expectedSize) {
            log.warn("区块数据大小不匹配: 期望 {}, 实际 {}", expectedSize, data != null ? data.length : 0);
            return null;
        }
        
        int[][][] blockStates = new int[size][height][size];
        int index = 0;
        
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < size; z++) {
                    int stateId = (data[index] & 0xFF) |
                                  ((data[index + 1] & 0xFF) << 8) |
                                  ((data[index + 2] & 0xFF) << 16) |
                                  ((data[index + 3] & 0xFF) << 24);
                    blockStates[x][y][z] = stateId;
                    index += 4;
                }
            }
        }
        
        return blockStates;
    }
    
    private void handleChunkUnload(ServerSyncUnloadChunkNVo packet) {
        if (clientWorld == null) {
            return;
        }
        
        clientWorld.removeChunk(packet.chunkX(), packet.chunkZ());
        log.debug("卸载区块: ({}, {})", packet.chunkX(), packet.chunkZ());
    }
    
    private void handleBlockUpdate(ServerSyncBlockUpdateNVo packet) {
        if (clientWorld == null) {
            return;
        }
        
        int chunkX = (int) Math.floor((float) packet.x() / com.ksptool.ourcraft.client.world.ClientChunk.CHUNK_SIZE);
        int chunkZ = (int) Math.floor((float) packet.z() / com.ksptool.ourcraft.client.world.ClientChunk.CHUNK_SIZE);
        com.ksptool.ourcraft.client.world.ClientChunk clientChunk = clientWorld.getChunk(chunkX, chunkZ);
        if (clientChunk != null) {
            int localX = packet.x() - chunkX * com.ksptool.ourcraft.client.world.ClientChunk.CHUNK_SIZE;
            int localZ = packet.z() - chunkZ * com.ksptool.ourcraft.client.world.ClientChunk.CHUNK_SIZE;
            clientChunk.setBlockState(localX, packet.y(), localZ, packet.blockId());
        }
        log.debug("收到方块更新: ({}, {}, {}) -> {}", packet.x(), packet.y(), packet.z(), packet.blockId());
    }
    
    private void handleEntityPositionAndRotation(ServerSyncEntityPositionAndRotationNVo packet) {
        if (gameClient == null) {
            return;
        }
        
        if (packet.entityId() == 0) {

            log.info("ServerSyncEntityPositionAndRotationNVo X:{} Y:{} Z:{} P:{} R:{}",packet.x(),packet.y(),packet.z(),packet.pitch(),packet.yaw());

            com.ksptool.ourcraft.client.entity.ClientPlayer player = gameClient.getPlayer();
            if (player != null) {
                org.joml.Vector3f serverPos = new org.joml.Vector3f((float) packet.x(), (float) packet.y(), (float) packet.z());
                float distance = player.getPosition().distance(serverPos);
                
                // 如果玩家尚未初始化完成，直接同步到服务端位置（避免初始位置不同步）
                if (!gameClient.isPlayerInitialized()) {
                    player.setPosition(serverPos); // 使用setPosition确保previousPosition也被正确初始化
                    player.setYaw(packet.yaw());
                    player.setPitch(packet.pitch());
                } else {
                    // 服务端协调：平滑地校正客户端预测位置
                    // 保存当前位置用于插值
                    player.getPreviousPosition().set(player.getPosition());
                    
                    // 如果差异较大，直接同步（可能是网络延迟或预测错误）
                    if (distance > 1.0f) {
                        player.getPosition().set(serverPos);
                        // 同时重置速度，避免继续预测错误的方向
                        player.getVelocity().set(0, 0, 0);
                    } else if (distance > 0.1f) {
                        // 差异较小，平滑插值到服务器位置
                        player.getPosition().lerp(serverPos, 0.5f);
                    }
                    // 如果差异很小（<0.1），不进行校正，保持客户端预测
                    
                    // 同步相机朝向（服务端是权威的）
                    player.setYaw(packet.yaw());
                    player.setPitch(packet.pitch());
                }
            }
        }
        log.debug("收到实体位置更新: entityId={}, pos=({}, {}, {})", packet.entityId(), packet.x(), packet.y(), packet.z());
    }
    
    private void handlePlayerState(ServerSyncPlayerStateNVo packet) {
        if (gameClient == null) {
            return;
        }
        
        com.ksptool.ourcraft.client.entity.ClientPlayer player = gameClient.getPlayer();
        if (player != null) {
            player.setHealth(packet.health());
            // foodLevel 对应饥饿值，暂时直接设置（后续可能需要转换）
            player.setHunger((float) packet.foodLevel());
            
            // 收到玩家状态同步包后，标记玩家已初始化完成
            // 这表示服务端已经完成了初始同步，客户端可以开始发送位置更新
            if (!gameClient.isPlayerInitialized()) {
                gameClient.setPlayerInitialized(true);
                log.info("玩家初始化完成，可以开始发送位置更新");
            }
        }
        log.debug("收到玩家状态更新: health={}, food={}", packet.health(), packet.foodLevel());
    }
    
    private void handleWorldTime(ServerSyncWorldTimeNVo packet) {
        if (clientWorld != null) {
            // 将游戏时间（tick）转换为一天中的时间（0.0-1.0）
            // 假设一天有24000 tick（与ServerWorld的TICKS_PER_DAY一致）
            long ticksPerDay = 24000L;
            float timeOfDay = (float) (packet.worldTime() % ticksPerDay) / ticksPerDay;
            clientWorld.setTimeOfDay(timeOfDay);
            log.debug("收到世界时间更新: {} -> {}", packet.worldTime(), timeOfDay);
        }
    }
    
    private void handleJoinServerResponse(RequestJoinServerNVo packet) {
        log.info("收到服务器加入响应: accepted={}, sessionId={}, pos=({}, {}, {})", 
            packet.accepted(), packet.sessionId(), packet.x(), packet.y(), packet.z());
        
        if (packet.accepted() == 1) {
            log.info("成功加入服务器，sessionId: {}", packet.sessionId());
            
            // 请求初始化多人游戏世界（将在主线程执行，避免OpenGL上下文问题）
            if (gameClient != null) {
                Double x = packet.x();
                Double y = packet.y();
                Double z = packet.z();
                Float yaw = packet.yaw();
                Float pitch = packet.pitch();
                
                if (x == null || y == null || z == null || yaw == null || pitch == null) {
                    log.error("服务器响应数据不完整: x={}, y={}, z={}, yaw={}, pitch={}", x, y, z, yaw, pitch);
                    return;
                }
                
                gameClient.requestInitializeMultiplayerWorld(x, y, z, yaw, pitch);
            } else {
                log.error("GameClient为null，无法初始化多人游戏世界");
            }
        } else {
            log.warn("加入服务器被拒绝: {}", packet.reason());
            disconnect();
        }
    }
    
    private void handleDisconnect(ServerDisconnectNVo packet) {
        log.warn("服务器断开连接: {}", packet.reason());
        disconnect();
    }
    
    /**
     * 向服务器发送数据包
     */
    public void sendPacket(Object packet) {
        if (!connected || socket.isClosed()) {
            log.warn("未连接到服务器，无法发送数据包");
            return;
        }
        
        try {
            KryoManager.writeObject(packet, outputStream);
        } catch (IOException e) {
            log.warn("发送数据包到服务器时发生错误: {}", e.getMessage());
            disconnect();
        }
    }
    
    /**
     * 断开连接
     */
    public void disconnect() {
        if (!connected) {
            return;
        }
        
        connected = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            log.warn("关闭服务器连接时发生错误: {}", e.getMessage());
        }
        
        if (receiveThread != null) {
            try {
                receiveThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * 检查是否已连接
     */
    public boolean isConnected() {
        return connected && socket != null && !socket.isClosed();
    }
    
    /**
     * 设置客户端世界引用
     */
    public void setClientWorld(ClientWorld clientWorld) {
        this.clientWorld = clientWorld;
    }
    
    /**
     * 从队列中取出数据包进行处理（由GameClient主线程调用）
     */
    public void processPackets() {
        Object packet;
        while ((packet = packetQueue.poll()) != null) {
            handlePacket(packet);
        }
    }
}

