package com.ksptool.ourcraft.debug;

import com.ksptool.ourcraft.sharedcore.network.KryoManager;
import com.ksptool.ourcraft.sharedcore.network.nvo.HuChunkUnloadNVo;
import com.ksptool.ourcraft.sharedcore.network.packets.*;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 调试客户端网络连接管理器
 */
@Slf4j
public class DebugNetworkConnection {
    
    private Socket socket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private volatile boolean connected = false;
    private final BlockingQueue<Object> packetQueue = new LinkedBlockingQueue<>();
    private DebugClient debugClient;
    
    public DebugNetworkConnection(DebugClient debugClient) {
        this.debugClient = debugClient;
    }
    
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
            
            Thread.ofVirtual().start(this::receiveLoop);
            
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
        
        if (packet instanceof ServerSyncChunkDataNVo) {
            handleChunkData((ServerSyncChunkDataNVo) packet);
            return;
        }
        if (packet instanceof HuChunkUnloadNVo) {
            handleChunkUnload((HuChunkUnloadNVo) packet);
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
        if (packet instanceof RequestJoinServerNVo) {
            handleJoinServerResponse((RequestJoinServerNVo) packet);
            return;
        }
        if (packet instanceof ServerDisconnectNVo) {
            handleDisconnect((ServerDisconnectNVo) packet);
            return;
        }
        
        log.debug("收到未处理的数据包: {}", packet.getClass().getName());
    }
    
    private void handleChunkData(ServerSyncChunkDataNVo packet) {
        int chunkX = packet.chunkX();
        int chunkZ = packet.chunkZ();
        byte[] blockData = packet.blockData();
        
        log.info("处理区块数据: ({}, {}), 数据长度: {}", chunkX, chunkZ, blockData != null ? blockData.length : 0);
        
        int[][][] blockStates = deserializeChunkData(blockData);
        if (blockStates == null) {
            log.warn("区块数据反序列化失败: ({}, {})", chunkX, chunkZ);
            return;
        }
        
        if (debugClient != null) {
            debugClient.handleChunkData(chunkX, chunkZ, blockStates);
        }
    }
    
    private int[][][] deserializeChunkData(byte[] data) {
        int size = DebugChunk.CHUNK_SIZE;
        int height = DebugChunk.CHUNK_HEIGHT;
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
    
    private void handleChunkUnload(HuChunkUnloadNVo packet) {
        if (debugClient != null) {
            debugClient.handleChunkUnload(packet.chunkX(), packet.chunkZ());
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
    
    private void handleJoinServerResponse(RequestJoinServerNVo packet) {
        log.info("收到服务器加入响应: accepted={}, sessionId={}, pos=({}, {}, {})", 
            packet.accepted(), packet.sessionId(), packet.x(), packet.y(), packet.z());
        
        if (packet.accepted() == 1) {
            log.info("成功加入服务器，sessionId: {}", packet.sessionId());
            
            if (debugClient != null) {
                Double x = packet.x();
                Double y = packet.y();
                Double z = packet.z();
                Float yaw = packet.yaw();
                Float pitch = packet.pitch();
                
                if (x != null && y != null && z != null && yaw != null && pitch != null) {
                    debugClient.setPlayerPosition(x, y, z, yaw, pitch);
                    sendPacket(new ClientReadyNDto());
                }
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
}

