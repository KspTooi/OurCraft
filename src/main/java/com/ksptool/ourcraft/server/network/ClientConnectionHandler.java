package com.ksptool.ourcraft.server.network;

import com.ksptool.ourcraft.server.OurCraftServerInstance;
import com.ksptool.ourcraft.server.entity.ServerPlayer;
import com.ksptool.ourcraft.sharedcore.network.KryoManager;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * 客户端连接处理器，每个客户端连接对应一个实例
 * 在虚拟线程中运行，负责处理单个客户端的所有网络交互
 */
@Slf4j
public class ClientConnectionHandler implements Runnable {
    
    private final Socket socket;
    private final OurCraftServerInstance ourCraftServerInstance;
    private final InputStream inputStream;
    private final OutputStream outputStream;
    private volatile boolean running = true;
    
    @Getter
    @Setter
    private ServerPlayer player;
    
    @Getter
    @Setter
    private Integer lastChunkX;
    
    @Getter
    @Setter
    private Integer lastChunkZ;
    
    @Getter
    @Setter
    private volatile boolean playerInitialized = false;
    
    public ClientConnectionHandler(Socket socket, OurCraftServerInstance ourCraftServerInstance) throws IOException {
        this.socket = socket;
        this.ourCraftServerInstance = ourCraftServerInstance;
        this.inputStream = socket.getInputStream();
        this.outputStream = socket.getOutputStream();
    }
    
    @Override
    public void run() {
        log.info("客户端连接已建立: {}", socket.getRemoteSocketAddress());
        
        try {
            while (running && !socket.isClosed()) {
                try {
                    // 从输入流读取数据包
                    Object packet = KryoManager.readObject(inputStream);
                    
                    // 将数据包分发给GameServer处理
                    ourCraftServerInstance.handlePacket(this, packet);
                } catch (IOException e) {
                    if (running) {
                        log.warn("读取客户端数据包时发生错误: {}", e.getMessage());
                    }
                    break;
                } catch (Exception e) {
                    log.error("处理客户端数据包时发生错误", e);
                    break;
                }
            }
        } finally {
            close();
        }
        
        log.info("客户端连接已关闭: {}", socket.getRemoteSocketAddress());
    }
    
    private final Object sendLock = new Object();
    
    /**
     * 向客户端发送数据包
     */
    public void sendPacket(Object packet) {
        if (!running || socket.isClosed()) {
            return;
        }
        
        if (packet == null) {
            log.warn("尝试发送null数据包");
            return;
        }
        
        synchronized (sendLock) {
        try {
            log.debug("发送数据包到客户端: {}", packet.getClass().getSimpleName());
            KryoManager.writeObject(packet, outputStream);
        } catch (IOException e) {
            log.warn("发送数据包到客户端时发生错误: {}", e.getMessage());
            close();
            }
        }
    }
    
    /**
     * 关闭连接
     */
    public void close() {
        if (!running) {
            return;
        }
        
        running = false;
        
        // 如果有关联的玩家实体，通知GameServer移除
        if (player != null && ourCraftServerInstance != null) {
            ourCraftServerInstance.onClientDisconnected(this);
        }
        
        try {
            socket.close();
        } catch (IOException e) {
            log.warn("关闭客户端连接时发生错误: {}", e.getMessage());
        }
    }
    
    /**
     * 检查连接是否活跃
     */
    public boolean isConnected() {
        return running && !socket.isClosed();
    }
}

