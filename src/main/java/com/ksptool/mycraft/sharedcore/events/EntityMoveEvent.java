package com.ksptool.mycraft.sharedcore.events;

import org.joml.Vector3f;

/**
 * 实体移动事件，当实体位置改变时触发
 */
public class EntityMoveEvent extends GameEvent {
    private final int entityId;
    private final Vector3f newPosition;
    private final Vector3f oldPosition;
    
    public EntityMoveEvent(int entityId, Vector3f newPosition, Vector3f oldPosition) {
        this.entityId = entityId;
        this.newPosition = new Vector3f(newPosition);
        this.oldPosition = new Vector3f(oldPosition);
    }
    
    public int getEntityId() {
        return entityId;
    }
    
    public Vector3f getNewPosition() {
        return new Vector3f(newPosition);
    }
    
    public Vector3f getOldPosition() {
        return new Vector3f(oldPosition);
    }
}

