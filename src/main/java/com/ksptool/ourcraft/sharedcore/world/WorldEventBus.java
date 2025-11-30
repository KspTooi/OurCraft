package com.ksptool.ourcraft.sharedcore.world;

import java.util.List;
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


    /**
     * 获取一批世界事件
     * @return 世界事件列表
     */
    List<WorldEvent> getBatchEvents();


    /**
     * 是否有下一个事件
     * @return 是否有下一个事件
     */ 
    boolean hasNext();

    /**
     * 获取下一个事件
     * @return 下一个事件
     */
    WorldEvent next();

}
