package com.ksptool.mycraft.sharedcore.events;

/**
 * 游戏事件基类，所有事件都继承此类
 */
public abstract class GameEvent {
    private final long timestamp;
    
    public GameEvent() {
        this.timestamp = System.nanoTime();
    }
    
    public long getTimestamp() {
        return timestamp;
    }
}

