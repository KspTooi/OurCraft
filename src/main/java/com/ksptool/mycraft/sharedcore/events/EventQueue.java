package com.ksptool.mycraft.sharedcore.events;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 线程安全的事件队列，用于服务端和客户端之间的通信
 * 使用两个独立的队列来避免竞态条件：
 * - C2S (Client to Server): 客户端发送给服务端的事件（如输入事件）
 * - S2C (Server to Client): 服务端发送给客户端的事件（如世界数据）
 */
public class EventQueue {
    private static final EventQueue instance = new EventQueue();
    
    // 客户端到服务端的队列（输入事件等）
    private final ConcurrentLinkedQueue<GameEvent> c2sQueue = new ConcurrentLinkedQueue<>();
    
    // 服务端到客户端的队列（世界数据等）
    private final ConcurrentLinkedQueue<GameEvent> s2cQueue = new ConcurrentLinkedQueue<>();
    
    private EventQueue() {
    }
    
    public static EventQueue getInstance() {
        return instance;
    }
    
    /**
     * 客户端发送事件到服务端
     */
    public void offerC2S(GameEvent event) {
        if (event != null) {
            c2sQueue.offer(event);
        }
    }
    
    /**
     * 服务端从队列中取出所有客户端事件
     */
    public java.util.List<GameEvent> pollAllC2S() {
        java.util.List<GameEvent> events = new java.util.ArrayList<>();
        GameEvent event;
        while ((event = c2sQueue.poll()) != null) {
            events.add(event);
        }
        return events;
    }
    
    /**
     * 服务端发送事件到客户端
     */
    public void offerS2C(GameEvent event) {
        if (event != null) {
            s2cQueue.offer(event);
        }
    }
    
    /**
     * 客户端从队列中取出所有服务端事件
     */
    public java.util.List<GameEvent> pollAllS2C() {
        java.util.List<GameEvent> events = new java.util.ArrayList<>();
        GameEvent event;
        while ((event = s2cQueue.poll()) != null) {
            events.add(event);
        }
        return events;
    }
    
    /**
     * 清空所有队列
     */
    public void clear() {
        c2sQueue.clear();
        s2cQueue.clear();
    }
    
    /**
     * 获取C2S队列大小
     */
    public int sizeC2S() {
        return c2sQueue.size();
    }
    
    /**
     * 获取S2C队列大小
     */
    public int sizeS2C() {
        return s2cQueue.size();
    }
    
    /**
     * @deprecated 使用 offerC2S 或 offerS2C 替代
     */
    @Deprecated
    public void offer(GameEvent event) {
        offerC2S(event);
    }
    
    /**
     * @deprecated 使用 pollAllC2S 或 pollAllS2C 替代
     */
    @Deprecated
    public java.util.List<GameEvent> pollAll() {
        return pollAllC2S();
    }
    
    /**
     * @deprecated 使用 sizeC2S 或 sizeS2C 替代
     */
    @Deprecated
    public int size() {
        return sizeC2S();
    }
}

