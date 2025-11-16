package com.ksptool.mycraft.sharedcore.events;

/**
 * 玩家输入事件，用于传递玩家的移动和跳跃输入
 */
public class PlayerInputEvent extends GameEvent {
    private final boolean forward;
    private final boolean backward;
    private final boolean left;
    private final boolean right;
    private final boolean jump;
    
    public PlayerInputEvent(boolean forward, boolean backward, boolean left, boolean right, boolean jump) {
        this.forward = forward;
        this.backward = backward;
        this.left = left;
        this.right = right;
        this.jump = jump;
    }
    
    public boolean isForward() {
        return forward;
    }
    
    public boolean isBackward() {
        return backward;
    }
    
    public boolean isLeft() {
        return left;
    }
    
    public boolean isRight() {
        return right;
    }
    
    public boolean isJump() {
        return jump;
    }
}

