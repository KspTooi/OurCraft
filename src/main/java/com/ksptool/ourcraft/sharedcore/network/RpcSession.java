package com.ksptool.ourcraft.sharedcore.network;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;

/**
 * 远程过程调用会话
 * 负责管理远程过程调用请求和响应
 */
@Slf4j
public class RpcSession {

    //远程过程调用ID
    private final AtomicLong rpcId = new AtomicLong(0);

    private final Socket socket;

    private final InputStream is;

    private final OutputStream os;
    
    //发送队列
    private final BlockingQueue<Object> sndQueue = new LinkedBlockingQueue<>();

    //接收队列
    private final BlockingQueue<Object> rcvQueue = new LinkedBlockingQueue<>();

    //是否正在运行
    private final AtomicBoolean running = new AtomicBoolean(false);

    //线程池
    private final ExecutorService executorService;

    //当前正在等待响应的RPC
    private final ConcurrentHashMap<Long, CompletableFuture<Object>> rpcFutures = new ConcurrentHashMap<>();

    public RpcSession(Socket socket,ExecutorService executorService) {
        this.socket = socket;
        this.executorService = executorService;
        try {
            this.is = socket.getInputStream();
            this.os = socket.getOutputStream();
            start();
        } catch (IOException e) {
            log.error("创建RPC会话失败: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }
    
    /**
     * 启动会话
     */
    private void start(){
        running.set(true);
        executorService.submit(this::sendLoop);
        executorService.submit(this::receiveLoop);
    }

    /**
     * 发送远程过程调用请求
     * @param packet 请求数据包
     */
    public CompletableFuture<Object> rpcRequest(Object data){
        RpcRequest<Object> request = RpcRequest.of(rpcId.incrementAndGet(), data);
        CompletableFuture<Object> future = new CompletableFuture<>();
        rpcFutures.put(request.requestId(), future);
        sendNext(request);

        future.orTimeout(30, TimeUnit.SECONDS).exceptionally(e -> {
            log.error("RPC请求超时: {}", e.getMessage());
            rpcFutures.remove(request.requestId());
            return null;
        });

        return future;
    }

    /**
     * 发送远程过程调用响应
     * @param response 响应数据包
     */
    public void rpcResponse(long requestId,Object data){
        RpcResponse<Object> response = RpcResponse.of(requestId, data);
        sendNext(response);
    }


    /**
     * 发送下一个数据包
     * @param packet 数据包
     */
    public void sendNext(Object packet){
        sndQueue.offer(packet);
    }

    /**
     * 接收下一个数据包
     * @return 数据包,如果队列为空,则返回null
     */
    public Object receiveNext(){
        return rcvQueue.poll();
    }

    /**
     * 接收下一个数据包
     * @param timeout 超时时间
     * @param unit 时间单位
     * @return 数据包,如果队列为空,则返回null
     */
    public Object receiveNext(long timeout, TimeUnit unit){
        try {
            return rcvQueue.poll(timeout, unit);
        } catch (InterruptedException e) {
            log.error("接收数据包失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 发送数据包循环
     */
    private void sendLoop(){
        while (running.get()) {
            try {
                //阻塞获取发送队列中的数据包
                Object packet = sndQueue.take();
                //写入输出流
                KryoManager.writeObject(packet, os);
            } catch (Exception e) {
                log.error("发送数据包失败: {}", e.getMessage());
                break;
            }
        }
    }

    /**
     * 接收数据包循环
     */
    private void receiveLoop(){
        while (running.get()) {
            try {
                //阻塞获取接收队列中的数据包
                Object packet = KryoManager.readObject(is);

                //如果接收到的是RPC响应 需要查询未完成RPC请求并完成响应
                if(packet instanceof RpcResponse<?> response){
                    CompletableFuture<Object> future = rpcFutures.get(response.requestId());
                    if(future != null){
                        future.complete(response.data());
                        rpcFutures.remove(response.requestId());
                        continue;
                    }
                    log.warn("未找到对应的RPC请求: {}", response.requestId());
                    continue;
                }

                //如果接收到的是其他数据包 直接放入接收队列
                rcvQueue.offer(packet);
            } catch (IOException e) {
                log.error("接收数据包失败: {}", e.getMessage());
                break;
            }
        }
    }

    /**
     * 关闭会话
     */
    public void close(){
        running.set(false);
        try {
            is.close();
            os.close();
            socket.close();
        } catch (IOException e) {
            log.error("关闭会话失败: {}", e.getMessage());
        }
    }

}
