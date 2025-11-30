package com.ksptool.ourcraft.server.world;

import com.ksptool.ourcraft.sharedcore.world.WorldEvent;
import com.ksptool.ourcraft.sharedcore.world.WorldEventBus;

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

}
