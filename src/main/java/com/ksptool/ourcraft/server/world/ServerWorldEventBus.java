package com.ksptool.ourcraft.server.world;

import com.ksptool.ourcraft.sharedcore.world.WorldEvent;
import com.ksptool.ourcraft.sharedcore.world.WorldEventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

public class ServerWorldEventBus implements WorldEventBus {

    // 使用非阻塞并发队列，适合高并发写入
    private final Queue<WorldEvent> eventQueue = new ConcurrentLinkedQueue<>();

    @Override
    public void publish(WorldEvent event) {
        eventQueue.offer(event);
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
