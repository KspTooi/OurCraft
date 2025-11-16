package com.ksptool.mycraft.sharedcore.events;

/**
 * 玩家动作事件，用于传递玩家的动作意图（而非原始输入）
 */
public class PlayerActionEvent extends GameEvent {
    private final PlayerAction action;
    
    public PlayerActionEvent(PlayerAction action) {
        this.action = action;
    }
    
    public PlayerAction getAction() {
        return action;
    }
}

