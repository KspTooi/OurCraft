package com.ksptool.mycraft.sharedcore.events;

/**
 * 玩家快捷栏切换事件，用于传递快捷栏切换操作
 */
public class PlayerHotbarSwitchEvent extends GameEvent {
    private final int slotDelta;
    
    public PlayerHotbarSwitchEvent(int slotDelta) {
        this.slotDelta = slotDelta;
    }
    
    public int getSlotDelta() {
        return slotDelta;
    }
}

