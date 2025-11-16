package com.ksptool.mycraft.sharedcore.events;

/**
 * 时间更新事件，用于同步游戏时间
 */
public class TimeUpdateEvent extends GameEvent {
    private final float timeOfDay;
    
    public TimeUpdateEvent(float timeOfDay) {
        this.timeOfDay = timeOfDay;
    }
    
    public float getTimeOfDay() {
        return timeOfDay;
    }
}

