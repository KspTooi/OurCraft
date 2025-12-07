package com.ksptool.ourcraft.server;

import com.ksptool.ourcraft.sharedcore.ServerEvent;
import com.ksptool.ourcraft.sharedcore.world.SharedWorld;
import com.ksptool.ourcraft.sharedcore.world.WorldEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * 服务端Dock事件服务
 * 负责处理服务端的所有Dock事件
 */
@Slf4j
public class ServerEventService {

    //事件类型->事件处理器
    private final Map<Class<? extends ServerEvent>, List<Consumer<ServerEvent>>> listeners = new HashMap<>();

    //使用非阻塞并发队列，适合高并发写入
    private final Queue<ServerEvent> eventQueue = new ConcurrentLinkedQueue<>();

    public <T extends ServerEvent> void subscribe(Class<T> eventType, Consumer<T> listener) {

        if(eventType == null || listener == null){
            throw new IllegalArgumentException("事件类型或事件处理器不能为空");
        }

        listeners.computeIfAbsent(eventType, k -> new ArrayList<>()).add((Consumer<ServerEvent>) listener);
    }

    public void publish(ServerEvent event) {
        eventQueue.offer(event);
    }

    /**
     * 处理事件
     */
    public void action() {
        ServerEvent event;
        while ((event = eventQueue.poll()) != null) {
            List<Consumer<ServerEvent>> handlers = listeners.get(event.getClass());
            if (handlers == null) {
                //log.warn("已丢弃事件: {} 原因: 该事件没有监听者", event.getClass().getName());
                continue;
            }
            for (Consumer<ServerEvent> handler : handlers) {
                try{
                    handler.accept(event);
                }catch(Exception e){
                    log.error("处理事件时发生错误: {}", e.getMessage());
                }
            }
        }
    }


}
