package com.ksptool.ourcraft.server.player;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import com.ksptool.ourcraft.server.OurCraftServer;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import xyz.downgoon.snowflake.Snowflake;

/**
 * 服务端玩家服务,负责处理玩家与网络相关的逻辑
 */
@Slf4j
public class ServerPlayerService {

    //用于生成会话ID的雪花算法
    private final Snowflake snowflake = new Snowflake(1, 1);

    private final ExecutorService networkThreadPool;

    private final OurCraftServer server;

    private final int port;

    private AtomicBoolean running = new AtomicBoolean(false);

    private Future<Void> listenerThread;

    private ServerSocket serverSocket;

    //所有连接的客户端
    @Getter
    private final List<PlayerSession> clients = new CopyOnWriteArrayList<>();

    //所有连接的客户端的会话ID到客户端的映射
    @Getter
    private final ConcurrentHashMap<Long, PlayerSession> sessions = new ConcurrentHashMap<>();


    public ServerPlayerService(OurCraftServer server, int port) {
        this.server = server;
        this.port = port;
        this.networkThreadPool = server.getNETWORK_THREAD_POOL();
    }

    /**
     * 启动网络服务
     */
    public void start(){
        if(running.get()){
           return;
        }

        //从线程池中获取一个线程
        listenerThread = networkThreadPool.submit(() -> {
            try{

                serverSocket = new ServerSocket(port);
                running.set(true);
                log.info("网络服务已就绪，当前端口: {}", port);

                while (true) {
                    
                    if(serverSocket.isClosed()){
                        break;
                    }

                    try {
                        Socket clientSocket = serverSocket.accept();

                        long sessionId = snowflake.nextId();
                        log.info("新客户端会话: {} 会话ID: {}", clientSocket.getRemoteSocketAddress(), sessionId);

                        PlayerSession handler = new PlayerSession(clientSocket, server, sessionId);
                        clients.add(handler);
                        sessions.put(sessionId, handler);
                        networkThreadPool.submit(handler);
                    } catch (IOException e) {
                        log.warn("接受客户端连接时发生错误: {}", e.getMessage());
                    }

                }

                return null;
            }catch(Exception e){
                log.error("启动网络监听器失败", e);
                return null;
            }
        });


    }

    /**
     * 停止网络服务
     */
    public void stop(){
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                log.warn("关闭ServerSocket时发生错误: {}", e.getMessage());
            }
        }

        if (listenerThread != null) {
            try {
                listenerThread.get();
            } catch (InterruptedException | ExecutionException e) {
                Thread.currentThread().interrupt();
            }
        }
        running.set(false);
        log.info("网络服务已停止");
    }

}
