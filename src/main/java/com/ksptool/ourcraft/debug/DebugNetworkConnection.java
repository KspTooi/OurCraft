package com.ksptool.ourcraft.debug;

import com.ksptool.ourcraft.sharedcore.network.KryoManager;
import com.ksptool.ourcraft.sharedcore.network.RpcRequest;
import com.ksptool.ourcraft.sharedcore.network.RpcResponse;
import com.ksptool.ourcraft.sharedcore.network.ndto.AuthRpcDto;
import com.ksptool.ourcraft.sharedcore.network.ndto.BatchDataFinishNDto;
import com.ksptool.ourcraft.sharedcore.network.ndto.PsAllowNDto;
import com.ksptool.ourcraft.sharedcore.network.ndto.PsFinishNDto;
import com.ksptool.ourcraft.sharedcore.network.nvo.AuthRpcVo;
import com.ksptool.ourcraft.sharedcore.network.nvo.BatchDataNVo;
import com.ksptool.ourcraft.sharedcore.network.nvo.PsChunkNVo;
import com.ksptool.ourcraft.sharedcore.network.nvo.PsJoinWorldNVo;
import com.ksptool.ourcraft.sharedcore.network.nvo.PsNVo;
import com.ksptool.ourcraft.sharedcore.network.nvo.PsPlayerNVo;
import com.ksptool.ourcraft.sharedcore.network.packets.*;
import com.ksptool.ourcraft.sharedcore.utils.FlexChunkSerializer;
import com.ksptool.ourcraft.sharedcore.utils.FlexChunkData;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 调试客户端网络连接管理器
 */
@Slf4j
public class DebugNetworkConnection {

    private enum ProtocolStage {
        NEW,
        AUTHORIZED,
        PROCESSED,
        PROCESS_SWITCHING,
        PROCESS_SWITCHED,
        IN_WORLD
    }

    private Socket socket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private volatile boolean connected = false;
    private volatile ProtocolStage stage = ProtocolStage.NEW;
    private final BlockingQueue<Object> packetQueue = new LinkedBlockingQueue<>();
    private DebugClient debugClient;
    private long sessionId = -1;
    private final AtomicLong requestIdCounter = new AtomicLong(1);

    public DebugNetworkConnection(DebugClient debugClient) {
        this.debugClient = debugClient;
    }

    public boolean connect(String host, int port, String playerName, String clientVersion) {
        if (connected) {
            log.warn("已经连接到服务器");
            return false;
        }

        try {
            socket = new Socket(host, port);
            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();
            connected = true;
            stage = ProtocolStage.NEW;

            log.info("已连接到服务器: {}:{}", host, port);

            Thread.ofVirtual().start(this::receiveLoop);

            long requestId = requestIdCounter.getAndIncrement();
            AuthRpcDto authDto = AuthRpcDto.of(playerName, clientVersion);
            RpcRequest<AuthRpcDto> authRequest = RpcRequest.of(requestId, authDto);
            sendPacket(authRequest);
            log.info("已发送认证请求: 玩家名称={}, 客户端版本={}", playerName, clientVersion);

            return true;
        } catch (IOException e) {
            log.error("连接服务器失败: {}", e.getMessage());
            connected = false;
            return false;
        }
    }

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

    public void processPackets() {
        while (!packetQueue.isEmpty()) {
            Object packet = packetQueue.poll();
            if (packet != null) {
                handlePacket(packet);
            }
        }
    }

    private void handlePacket(Object packet) {
        if (packet == null) {
            return;
        }

        if (packet instanceof RpcResponse(long requestId, Object data)) {
            handleRpcResponse(requestId, data);
            return;
        }

        if (packet instanceof BatchDataNVo batchData) {
            handleBatchData(batchData);
            return;
        }

        if (packet instanceof PsNVo psNVo) {
            handleProcessSwitch(psNVo);
            return;
        }

        if (packet instanceof PsChunkNVo psChunk) {
            handleProcessSwitchChunk(psChunk);
            return;
        }

        if (packet instanceof PsPlayerNVo psPlayer) {
            handleProcessSwitchPlayer(psPlayer);
            return;
        }

        if (packet instanceof PsJoinWorldNVo) {
            handleJoinWorld();
            return;
        }

        if (packet instanceof ServerSyncBlockUpdateNVo) {
            handleBlockUpdate((ServerSyncBlockUpdateNVo) packet);
            return;
        }

        if (packet instanceof ServerSyncEntityPositionAndRotationNVo) {
            handleEntityPositionAndRotation((ServerSyncEntityPositionAndRotationNVo) packet);
            return;
        }

        if (packet instanceof ServerDisconnectNVo) {
            handleDisconnect((ServerDisconnectNVo) packet);
            return;
        }

        log.debug("收到未处理的数据包: {}", packet.getClass().getName());
    }

    private void handleRpcResponse(long requestId, Object data) {
        if (data instanceof AuthRpcVo authRpcVo) {
            handleAuthResponse(authRpcVo);
            return;
        }
        log.warn("收到未知的RPC响应类型: requestId={}, dataType={}", requestId, data != null ? data.getClass().getName() : "null");
    }

    private void handleAuthResponse(AuthRpcVo authRpcVo) {
        if (stage != ProtocolStage.NEW) {
            log.warn("收到认证响应，但当前阶段不是NEW: {}", stage);
            return;
        }

        if (authRpcVo.accepted() != 0) {
            log.error("认证失败: {}", authRpcVo.reason());
            disconnect();
            return;
        }

        sessionId = authRpcVo.sessionId();
        stage = ProtocolStage.AUTHORIZED;
        log.info("认证成功，会话ID: {}", sessionId);
    }

    private void handleBatchData(BatchDataNVo batchData) {
        if (stage != ProtocolStage.AUTHORIZED) {
            log.warn("收到批数据，但当前阶段不是AUTHORIZED: {}", stage);
            return;
        }

        log.info("收到批数据: kind={}, dataLength={}", batchData.kind(), batchData.data() != null ? batchData.data().length : 0);
        
        sendPacket(BatchDataFinishNDto.of());
        stage = ProtocolStage.PROCESSED;
        log.info("已确认批数据");
    }

    private void handleProcessSwitch(PsNVo psNVo) {
        if (stage != ProtocolStage.PROCESSED) {
            log.warn("收到进程切换通知，但当前阶段不是PROCESSED: {}", stage);
            return;
        }

        log.info("收到进程切换通知: 世界={}, APS={}, 总Action数={}, 开始时间={}", 
                psNVo.worldName(), psNVo.aps(), psNVo.totalActions(), psNVo.startDateTime());

        sendPacket(new PsAllowNDto());
        stage = ProtocolStage.PROCESS_SWITCHING;
        log.info("已确认进程切换");
    }

    private void handleProcessSwitchChunk(PsChunkNVo psChunk) {
        if (stage != ProtocolStage.PROCESS_SWITCHING) {
            log.warn("收到进程切换区块数据，但当前阶段不是PROCESS_SWITCHING: {}", stage);
            return;
        }

        int chunkX = psChunk.chunkX();
        int chunkZ = psChunk.chunkZ();
        byte[] blockData = psChunk.blockData();

        log.info("处理进程切换区块数据: ({}, {}), 数据长度: {}", chunkX, chunkZ, blockData != null ? blockData.length : 0);

        if (blockData == null || blockData.length == 0) {
            log.warn("区块数据为空: ({}, {})", chunkX, chunkZ);
            return;
        }

        try {
            FlexChunkData flexChunkData = FlexChunkSerializer.deserialize(blockData);
            int width = flexChunkData.getWidth();
            int height = flexChunkData.getHeight();
            int depth = flexChunkData.getDepth();

            int[][][] blockStates = deserializeChunkData(blockData);
            if (blockStates == null) {
                log.warn("区块数据反序列化失败: ({}, {})", chunkX, chunkZ);
                return;
            }

            if (debugClient != null) {
                debugClient.handleChunkData(chunkX, chunkZ, width, height, depth, blockStates);
            }
        } catch (Exception e) {
            log.error("处理进程切换区块数据时发生错误: ({}, {})", chunkX, chunkZ, e);
        }
    }

    private void handleProcessSwitchPlayer(PsPlayerNVo psPlayer) {
        if (stage != ProtocolStage.PROCESS_SWITCHING) {
            log.warn("收到进程切换玩家数据，但当前阶段不是PROCESS_SWITCHING: {}", stage);
            return;
        }

        log.info("收到进程切换玩家数据: UUID={}, 名称={}, 位置=({}, {}, {}), 朝向=({}, {})", 
                psPlayer.uuid(), psPlayer.name(), psPlayer.posX(), psPlayer.posY(), psPlayer.posZ(), 
                psPlayer.yaw(), psPlayer.pitch());

        if (debugClient != null) {
            debugClient.setPlayerPosition(psPlayer.posX(), psPlayer.posY(), psPlayer.posZ(), 
                    (float) psPlayer.yaw(), (float) psPlayer.pitch());
        }

        sendPacket(new PsFinishNDto());
        stage = ProtocolStage.PROCESS_SWITCHED;
        log.info("已确认进程切换完成");
    }

    private void handleJoinWorld() {
        if (stage != ProtocolStage.PROCESS_SWITCHED) {
            log.warn("收到加入世界通知，但当前阶段不是PROCESS_SWITCHED: {}", stage);
            return;
        }

        stage = ProtocolStage.IN_WORLD;
        log.info("已加入世界，可以开始游戏");
    }

    private int[][][] deserializeChunkData(byte[] data) {
        if (data == null || data.length == 0) {
            log.warn("区块数据为空");
            return null;
        }

        try {
            // 使用FlexChunkSerializer反序列化
            FlexChunkData flexChunkData = FlexChunkSerializer.deserialize(data);

            int width = flexChunkData.getWidth();
            int height = flexChunkData.getHeight();
            int depth = flexChunkData.getDepth();

            log.debug("反序列化区块尺寸: {}x{}x{}", width, height, depth);

            // 创建快照以安全读取数据
            FlexChunkData.Snapshot snapshot = flexChunkData.createSnapshot();

            // 转换为调试客户端使用的int[][][]格式
            int[][][] blockStates = new int[width][height][depth];

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    for (int z = 0; z < depth; z++) {
                        // 从快照中获取方块状态ID
                        var blockState = snapshot.getBlock(x, y, z);
                        if (blockState != null) {
                            // 使用全局调色板获取状态ID
                            blockStates[x][y][z] = com.ksptool.ourcraft.sharedcore.GlobalPalette.getInstance()
                                    .getStateId(blockState);
                        } else {
                            blockStates[x][y][z] = 0; // 空气
                        }
                    }
                }
            }

            return blockStates;

        } catch (Exception e) {
            log.error("反序列化区块数据时发生错误", e);
            return null;
        }
    }

    private void handleBlockUpdate(ServerSyncBlockUpdateNVo packet) {
        if (debugClient != null) {
            debugClient.handleBlockUpdate(packet.x(), packet.y(), packet.z(), packet.blockId());
        }
    }

    private void handleEntityPositionAndRotation(ServerSyncEntityPositionAndRotationNVo packet) {
        if (packet.entityId() == 0 && debugClient != null) {
            debugClient.updatePlayerPosition(packet.x(), packet.y(), packet.z(), packet.yaw(), packet.pitch());
        }
    }

    private void handleDisconnect(ServerDisconnectNVo packet) {
        log.warn("服务器断开连接: {}", packet.reason());
        disconnect();
    }

    public void sendPacket(Object packet) {
        if (!connected || outputStream == null) {
            return;
        }

        try {
            KryoManager.writeObject(packet, outputStream);
        } catch (IOException e) {
            log.error("发送数据包失败: {}", e.getMessage());
            disconnect();
        }
    }

    public void disconnect() {
        connected = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            log.error("关闭连接失败: {}", e.getMessage());
        }
    }

    public boolean isConnected() {
        return connected;
    }

    public boolean isInWorld() {
        return stage == ProtocolStage.IN_WORLD;
    }

    public long getSessionId() {
        return sessionId;
    }
}
