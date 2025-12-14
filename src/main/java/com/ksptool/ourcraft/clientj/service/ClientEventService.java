package com.ksptool.ourcraft.clientj.service;

import com.ksptool.ourcraft.clientj.commons.ClientEvent;
import com.ksptool.ourcraft.sharedcore.world.SharedWorld;
import lombok.extern.slf4j.Slf4j;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

@Slf4j
public class ClientEventService {

    //事件类型->事件处理器
    private final Map<Class<? extends ClientEvent>, List<Consumer<ClientEvent>>> listeners = new HashMap<>();

    private final Queue<ClientEvent> eventQueue = new ConcurrentLinkedQueue<>();


    /**
     * 订阅事件
     * @param eventType 事件类型
     * @param listener 事件处理器
     */
    public <T extends ClientEvent> void subscribe(Class<T> eventType, Consumer<T> listener) {

        if(eventType == null || listener == null){
            throw new IllegalArgumentException("事件类型或事件处理器不能为空");
        }

        if(!(listener instanceof Consumer<T>)){
            throw new IllegalArgumentException("事件处理器必须是" + eventType.getName() + "的子类");
        }

        listeners.computeIfAbsent(eventType, k -> new ArrayList<>()).add((Consumer<ClientEvent>) listener);
    }

    /**
     * 取消所有事件订阅
     * @param eventType 事件类型
     */
    public void unsubscribeAll() {
        listeners.clear();
        eventQueue.clear();
    }

    /**
     * 发布事件
     * @param event 事件
     */
    public void publish(ClientEvent event) {
        eventQueue.offer(event);
    }

    /**
     * 处理事件
     * @param delta 距离上一帧经过的时间（秒）
     * @param world 世界
     */
    public void action(double delta, SharedWorld world) {
        ClientEvent event;
        while ((event = eventQueue.poll()) != null) {
            List<Consumer<ClientEvent>> handlers = listeners.get(event.getClass());
            if (handlers == null) {
                log.warn("已丢弃事件: {} 原因: 该事件没有监听者", event.getClass().getName());
                continue;
            }
            for (Consumer<ClientEvent> handler : handlers) {
                try{
                    handler.accept(event);
                }catch(Exception e){
                    log.error("处理事件时发生错误: {}", e.getMessage());
                }
            }
        }
    }


    public boolean hasNext() {
        return !eventQueue.isEmpty();
    }

    public ClientEvent next() {
        return eventQueue.poll();
    }

}
