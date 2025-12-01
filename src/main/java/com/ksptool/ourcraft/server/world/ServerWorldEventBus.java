package com.ksptool.ourcraft.server.world;

import com.ksptool.ourcraft.sharedcore.world.WorldEvent;
import com.ksptool.ourcraft.sharedcore.world.WorldEventBus;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

@Slf4j
public class ServerWorldEventBus implements WorldEventBus {

    //事件类型->事件处理器
    private final Map<Class<? extends WorldEvent>, List<Consumer<WorldEvent>>> listeners = new HashMap<>();

    //使用非阻塞并发队列，适合高并发写入
    private final Queue<WorldEvent> eventQueue = new ConcurrentLinkedQueue<>();

    @Override
    public <T extends WorldEvent> void subscribe(Class<T> eventType, Consumer<T> listener) {

        if(eventType == null || listener == null){
            throw new IllegalArgumentException("事件类型或事件处理器不能为空");
        }

        if(!(listener instanceof Consumer<T>)){
            throw new IllegalArgumentException("事件处理器必须是" + eventType.getName() + "的子类");
        }

        listeners.computeIfAbsent(eventType, k -> new ArrayList<>()).add((Consumer<WorldEvent>) listener);
    }


    @Override
    public void publish(WorldEvent event) {
        eventQueue.offer(event);
    }

    @Override
    public void process() {
        WorldEvent event;
        while ((event = eventQueue.poll()) != null) {
            List<Consumer<WorldEvent>> handlers = listeners.get(event.getClass());
            if (handlers != null) {
                for (Consumer<WorldEvent> handler : handlers) {
                    try{
                        handler.accept(event);
                    }catch(Exception e){
                        log.error("处理事件时发生错误: {}", e.getMessage());
                    }
                }
            }
        }
    }


    /**
     * 处理所有积压的事件 (必须在 ServerWorld 的 Action 中调用)
     * @param handler 事件处理器
     */
    @Override
    public void processEvents(Consumer<WorldEvent> handler) {
        WorldEvent event;
        // 循环取出队列中所有事件直到为空
        while ((event = eventQueue.poll()) != null) {
            handler.accept(event);
        }
    }

    /**
     * 获取一批世界事件
     * @return 世界事件列表
     */
    @Override
    public List<WorldEvent> getBatchEvents() {
        List<WorldEvent> events = new ArrayList<>();
        while (!eventQueue.isEmpty()) {
            events.add(eventQueue.poll());
        }
        return events;
    }

    @Override
    public boolean hasNext() {
        return !eventQueue.isEmpty();
    }

    @Override
    public WorldEvent next() {
        return eventQueue.poll();
    }



}
