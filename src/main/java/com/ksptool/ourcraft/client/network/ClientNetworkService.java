package com.ksptool.ourcraft.client.network;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.ksptool.ourcraft.sharedcore.enums.EngineDefault;
import com.ksptool.ourcraft.sharedcore.network.ndto.AuthNDto;
import com.ksptool.ourcraft.sharedcore.network.ndto.BatchDataFinishNDto;
import com.ksptool.ourcraft.sharedcore.network.nvo.AuthNVo;
import com.ksptool.ourcraft.sharedcore.network.nvo.BatchDataNVo;
import com.ksptool.ourcraft.sharedcore.utils.ThreadFactoryUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClientNetworkService {

    //线程池
    public static final ExecutorService NETWORK_THREAD_POOL =  Executors.newThreadPerTaskExecutor(ThreadFactoryUtils.createNetworkThreadFactory());

    //当前连接到的服务器
    private ClientNetworkSession clientNetworkSession;

    //正在进行的连接任务
    private CompletableFuture<ClientNetworkSession> connectFuture;

    //玩家名称
    private String playerName = "KspTooi";

    //客户端版本
    private String clientVersion = EngineDefault.ENGINE_VERSION;

    /**
     * 连接到服务器
     * @param host 服务器主机名
     * @param port 服务器端口
     */
    public Future<ClientNetworkSession> connect(String host, int port) throws Exception {

        if(connectFuture != null && !connectFuture.isDone()){
            log.warn("已经连接到服务器");
            return connectFuture;
        }

        connectFuture = new CompletableFuture<>();
        log.info("正在连接到: {}:{}", host, port);

        //打开Socket
        Future<Socket> socketFuture = openSocket(host, port);
        var socket = socketFuture.get(30, TimeUnit.SECONDS);

        if(socket == null){
            log.error("连接到服务器失败");
            return null;
        }

        //处理认证
        var session = establishSession(socket).get();

        if(session == null){
            log.error("处理认证失败");
            return null;
        }

        //接收批数据
        while(true){
            var batchData = session.receiveNext(30, TimeUnit.SECONDS);

            if(batchData == null){
                log.warn("批数据接收超时");
                break;
            }

            //如果收到kind为-1的批数据，则表示批数据接收完成
            if(batchData instanceof BatchDataNVo vo){
                if(vo.kind() == -1){
                    log.info("会话:{} 批数据处理完成", session.getId());
                    // 发送批数据完成确认
                    session.sendNext(new BatchDataFinishNDto());
                    session.setStage(ClientNetworkSession.Stage.PROCESSED);
                    break;
                }
            }
        }

        clientNetworkSession = session;
        connectFuture.complete(session);
        return connectFuture;
    }

    /**
     * 获取当前网络会话
     */
    public ClientNetworkSession getSession() {
        return clientNetworkSession;
    }

    /**
     * 断开连接
     */
    public void disconnect() {
        if(clientNetworkSession == null){
            log.warn("没有连接到服务器");
            return;
        }
        clientNetworkSession.close();
        clientNetworkSession = null;
        connectFuture = null;
    }


    /**
     * 打开Socket
     * @param host 主机名
     * @param port 端口
     * @return Socket
     */
    private Future<Socket> openSocket(String host, int port) {

        var future = new CompletableFuture<Socket>();

        Thread.ofVirtual().start(() -> {
            try {
                Socket socket = new Socket(host, port);
                future.complete(socket);
            } catch (IOException e) {
                log.error("创建Socket失败: {}", e.getMessage());
                future.complete(null);
            }
        });

        return future;
    }

    /**
     * 建立会话
     * @param socket Socket
     * @return 会话
     */
    private Future<ClientNetworkSession> establishSession(Socket socket) {
        var future = new CompletableFuture<ClientNetworkSession>();
        Thread.ofVirtual().start(() -> {

            var session = new ClientNetworkSession(socket, NETWORK_THREAD_POOL);
            //发送认证包
            try {

                log.info("正在请求认证 玩家名称: {} 客户端版本: {}", playerName, clientVersion);
                var authRet = session.rpcRequest(AuthNDto.of(playerName, clientVersion)).get();

                if(authRet instanceof AuthNVo r){

                    //服务端接受认证 已分配会话ID
                    if(r.accepted() == 0){
                        session.setId(r.sessionId());
                        session.setStage(ClientNetworkSession.Stage.AUTHORIZED);
                        future.complete(session);
                        log.info("认证已接受 分配的会话ID: {} 玩家名称: {}", session.getId(), playerName);
                        return;
                    }

                    //服务端拒绝认证 返回拒绝原因
                    if(r.accepted() == 1){
                        log.error("认证被拒绝: {}", r.reason());
                        future.complete(null);
                        return;
                    }
                }

                //异常
                log.error("处理认证失败: {}", authRet);
                future.complete(null);
                return;

            } catch (Exception e) {
                log.error("处理认证失败: {}", e.getMessage());
                future.complete(null);
                return;
            }

        });
        return future;
    }




}
