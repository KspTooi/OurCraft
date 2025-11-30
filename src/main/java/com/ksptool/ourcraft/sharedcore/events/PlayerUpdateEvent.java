package com.ksptool.ourcraft.sharedcore.events;

import lombok.Getter;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * 玩家状态更新事件，由服务端发送给客户端，用于同步玩家位置、相机朝向等信息
 */
@Getter
public class PlayerUpdateEvent extends GameEvent {
    private final Vector3d position;
    private final Vector3d previousPosition;
    private final float yaw;
    private final float pitch;
    private final float previousYaw;
    private final float previousPitch;
    private final int selectedSlot;
    
    public PlayerUpdateEvent(Vector3d position, Vector3d previousPosition,
                             float yaw, float pitch, 
                             float previousYaw, float previousPitch,
                             int selectedSlot) {
        this.position = new Vector3d(position);
        this.previousPosition = new Vector3d(previousPosition);
        this.yaw = yaw;
        this.pitch = pitch;
        this.previousYaw = previousYaw;
        this.previousPitch = previousPitch;
        this.selectedSlot = selectedSlot;
    }
}


