package com.ksptool.mycraft.sharedcore.events;

/**
 * 方块更新事件，当方块状态改变时触发
 */
public class BlockUpdateEvent extends GameEvent {
    private final int x;
    private final int y;
    private final int z;
    private final int newStateId;
    private final int oldStateId;
    
    public BlockUpdateEvent(int x, int y, int z, int newStateId, int oldStateId) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.newStateId = newStateId;
        this.oldStateId = oldStateId;
    }
    
    public int getX() {
        return x;
    }
    
    public int getY() {
        return y;
    }
    
    public int getZ() {
        return z;
    }
    
    public int getNewStateId() {
        return newStateId;
    }
    
    public int getOldStateId() {
        return oldStateId;
    }
}

