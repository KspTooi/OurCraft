package com.ksptool.ourcraft.sharedcore.world;

import java.util.function.Consumer;

/**
 * 服务端世界内部事件的标记接口
 * 用于区分 SharedCore 中的网络 GameEvent
 */
public interface WorldEventBus {

    /**
     * 发布世界事件 可以在任何线程中发布事件
     * @param event 世界事件
     */
    void publish(WorldEvent event);

    /**
     * 处理世界事件
     * @param handler 事件处理函数
     */
    void processEvents(Consumer<WorldEvent> handler);

}
