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

    //Socket
    private final Socket socket;

    //输入流
    private final InputStream is;

    //输出流
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

    //当前正在等待指定类型数据包的Future
    private final ConcurrentHashMap<Class<?>, CompletableFuture<Object>> receiveFutures = new ConcurrentHashMap<>();

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
     * @param data 请求数据包
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
     * @param data 响应数据包
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
     * 接收下一个数据包(等待指定类型数据包)
     * @param clazz 数据包类型
     * @return 数据包,如果队列为空,则返回null
     */
    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<T> receiveNext(Class<T> clazz,long timeout, TimeUnit unit){

        // 如果队列里已经有包了，并且正好是我们想要的，直接返回
        Object head = rcvQueue.peek();

        if(head != null){
            if (clazz.isInstance(head)) {
                // 从队列取出
                Object packet = rcvQueue.poll();
                return CompletableFuture.completedFuture((T) packet);
            }
        }

        //已经有正在等待指定类型数据包的Future
        var future = receiveFutures.get(clazz);

        if(future != null){
            return (CompletableFuture<T>) future;
        }

        future = new CompletableFuture<>();
        receiveFutures.put(clazz, future);

        future.orTimeout(timeout, unit).exceptionally(e -> {
            log.error("接收数据包超时: {}", e.getMessage());
            receiveFutures.remove(clazz);
            return null;
        });

        return (CompletableFuture<T>) future;
    }

    public <T> T waitFor(Class<T> clazz, long timeout, TimeUnit unit) {
        try {
            return receiveNext(clazz, timeout, unit).get();
        } catch (java.util.concurrent.ExecutionException e) {
            // 抛出底层的超时异常或运行时异常，方便业务层 try-catch
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException("Wait for packet failed", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for packet", e);
        } catch (Exception e) {
            throw new RuntimeException(e);
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
                close();
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
                if(packet instanceof RpcResponse<?>(long requestId, Object data)){
                    CompletableFuture<Object> future = rpcFutures.get(requestId);
                    if(future != null){
                        future.complete(data);
                        rpcFutures.remove(requestId);
                        continue;
                    }
                    log.warn("未找到对应的RPC请求: {}", requestId);
                    continue;
                }

                //查询是否有指定类型的数据包正在等待
                var future = receiveFutures.get(packet.getClass());
                if(future != null){
                    future.complete(packet);
                    receiveFutures.remove(packet.getClass());
                    continue;
                }

                //如果接收到的是其他数据包 直接放入接收队列
                rcvQueue.offer(packet);
            } catch (IOException e) {
                log.error("接收数据包失败: {}", e.getMessage());
                close();
                break;
            }
        }
    }

    /**
     * 检查会话是否活跃
     * @return 是否活跃
     */
    public boolean isActive(){
        return running.get();
    }

    /**
     * 关闭会话
     */
    public void close(){
        running.set(false);

        //清空所有队列
        sndQueue.clear();
        rcvQueue.clear();
        rpcFutures.clear();

        try {
            //关闭IO流与Socket
            is.close();
            os.close();
            socket.close();
        } catch (IOException e) {
            log.error("关闭会话失败: {}", e.getMessage());
        }
    }

}
