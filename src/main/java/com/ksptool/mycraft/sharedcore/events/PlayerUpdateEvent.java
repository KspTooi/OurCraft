package com.ksptool.mycraft.sharedcore.events;

import lombok.Getter;
import org.joml.Vector3f;

/**
 * 玩家状态更新事件，由服务端发送给客户端，用于同步玩家位置、相机朝向等信息
 */
@Getter
public class PlayerUpdateEvent extends GameEvent {
    private final Vector3f position;
    private final Vector3f previousPosition;
    private final float yaw;
    private final float pitch;
    private final float previousYaw;
    private final float previousPitch;
    private final int selectedSlot;
    
    public PlayerUpdateEvent(Vector3f position, Vector3f previousPosition, 
                             float yaw, float pitch, 
                             float previousYaw, float previousPitch,
                             int selectedSlot) {
        this.position = new Vector3f(position);
        this.previousPosition = new Vector3f(previousPosition);
        this.yaw = yaw;
        this.pitch = pitch;
        this.previousYaw = previousYaw;
        this.previousPitch = previousPitch;
        this.selectedSlot = selectedSlot;
    }
}


