package com.ksptool.ourcraft.server.network;

import com.ksptool.ourcraft.server.OurCraftServer;
import com.ksptool.ourcraft.server.ServerConfigService;
import com.ksptool.ourcraft.sharedcore.GlobalService;
import com.ksptool.ourcraft.sharedcore.enums.EngineDefault;
import com.ksptool.ourcraft.sharedcore.network.ndto.AuthRpcDto;
import com.ksptool.ourcraft.sharedcore.network.ndto.PlayerInputNDto;
import com.ksptool.ourcraft.sharedcore.network.packets.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import xyz.downgoon.snowflake.Snowflake;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class ServerNetworkService implements GlobalService {

    //用于生成会话ID的雪花算法
    private final Snowflake snowflake = new Snowflake(1, 1);

    private final AtomicBoolean running = new AtomicBoolean(false);

    //所有连接的客户端的会话ID到客户端的映射
    @Getter
    private final ConcurrentHashMap<Long, NetworkSession> sessions = new ConcurrentHashMap<>();

    @Getter
    private final OurCraftServer server;

    @Getter
    private final ServerConfigService configService;

    private final ExecutorService ntp;

    private final String bindAddr;

    private final Integer bindPort; 

    //服务端主Socket
    private ServerSocket serverSocket;

    @Getter
    private final NetworkRouter nr;

    //网络监听线程
    @Getter
    private Future<?> listenerThread;

    public ServerNetworkService(OurCraftServer server) {
        this.nr = new NetworkRouter();
        this.configService = server.getConfigService();
        this.ntp = server.getNETWORK_THREAD_POOL();
        this.server = server;
        this.bindAddr = configService.read().getBindAddress();
        this.bindPort = configService.read().getPort();

        //注册网络处理器
        var psHandler = new ClientPsHandler(this);
        nr.subscribeRpc(AuthRpcDto.class,psHandler::playerAuth);

        var networkHandler = new ClientNetworkHandler();
        nr.subscribe(PlayerInputNDto.class,networkHandler::playerInput);
        nr.subscribe(ClientKeepAliveNDto.class,networkHandler::clientKeepAlive);
    }

    /**
     * 启动网络服务
     */
    public void start() {

        if(running.get()){
            log.warn("网络服务当前已在运行,无法重复启动");
            return;
         }
 
         //从线程池中获取一个线程
         listenerThread = ntp.submit(() -> {
             try{
 
                 serverSocket = new ServerSocket(bindPort, EngineDefault.MAX_NETWORK_BACKLOG_SIZE, InetAddress.getByName(bindAddr));
                 running.set(true);
                 log.info("网络服务已就绪，当前端口: {}", bindPort);
 
                 while (running.get()) {
                     
                     if(serverSocket.isClosed()){
                         break;
                     }

                     try {
                         Socket clientSocket = serverSocket.accept();

                         if(sessions.size() >= EngineDefault.MAX_CONCURRENT_SESSIONS){
                            log.warn("达到最大并发会话数限制,拒绝新连接:{}", clientSocket.getRemoteSocketAddress());
                            clientSocket.close();
                            continue;
                         }

                         long sessionId = snowflake.nextId();
                         log.info("新客户端会话: {} 会话ID: {}", clientSocket.getRemoteSocketAddress(), sessionId);
                         NetworkSession nSession = new NetworkSession(this, clientSocket, sessionId);
                         sessions.put(sessionId, nSession);
                         //ntp.submit(nSession);
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
    public void shutdown() {
        running.set(false);
        //关闭所有客户端
        for(NetworkSession session : sessions.values()){
            session.close();
        }
        //关闭服务器Socket
        if(serverSocket != null && !serverSocket.isClosed()){
            try{
                serverSocket.close();
            }catch(IOException e){
                log.warn("关闭服务器Socket时发生错误: {}", e.getMessage());
            }
        }
        log.info("网络服务已停止");
    }


}
