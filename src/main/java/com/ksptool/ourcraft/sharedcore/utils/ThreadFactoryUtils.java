package com.ksptool.ourcraft.sharedcore.utils;

import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 线程工厂工具类，提供统一的线程创建工厂
 */
@Slf4j
public class ThreadFactoryUtils {

    //SWEU线程索引计数器
    private static final AtomicInteger sweuThreadIndex = new AtomicInteger(0);

    //区块处理线程索引计数器
    private static final AtomicInteger chunkProcessThreadIndex = new AtomicInteger(0);

    //网络线程索引计数器
    private static final AtomicInteger networkThreadIndex = new AtomicInteger(0);



    /**
     * 创建服务端世界执行单元线程工厂
     * 用于运行世界逻辑ACTION
     * 
     * @return 线程工厂
     */
    public static ThreadFactory createSWEUThreadFactory() {
        return r -> {
            Thread thread = new Thread(r);
            thread.setName("SWEU-" + sweuThreadIndex.getAndIncrement());
            thread.setDaemon(false);
            thread.setUncaughtExceptionHandler((t, e) -> {
                log.error("SWEU线程 {} 发生未捕获异常", t.getName(), e);
            });
            log.debug("创建SWEU线程: {}", thread.getName());
            return thread;
        };
    }

    /**
     * 创建区块处理线程工厂
     * 用于处理区块加载、生成、卸载存盘等任务
     * 
     * @return 线程工厂
     */
    public static ThreadFactory createChunkProcessThreadFactory() {
        return r -> {
            Thread thread = new Thread(r);
            thread.setName("ChunkProcess-" + chunkProcessThreadIndex.getAndIncrement());
            thread.setDaemon(false);
            thread.setUncaughtExceptionHandler((t, e) -> {
                log.error("区块处理线程 {} 发生未捕获异常", t.getName(), e);
            });
            log.debug("创建区块处理线程: {}", thread.getName());
            return thread;
        };
    }

    /**
     * 创建网络线程工厂（虚拟线程）
     * 用于处理网络连接、心跳、数据包接收发送等任务
     * 
     * @return 线程工厂
     */
    public static ThreadFactory createNetworkThreadFactory() {
        return r -> {
            Thread thread = Thread.ofVirtual().unstarted(r);
            thread.setName("VT-Network-" + networkThreadIndex.getAndIncrement());
            thread.setUncaughtExceptionHandler((t, e) -> {
                log.error("网络线程 {} 发生未捕获异常", t.getName(), e);
            });
            log.debug("创建网络线程: {}", thread.getName());
            return thread;
        };
    }

}

