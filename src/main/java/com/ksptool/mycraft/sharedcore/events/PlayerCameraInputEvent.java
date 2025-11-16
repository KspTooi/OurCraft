package com.ksptool.mycraft.sharedcore.events;

import lombok.Getter;

/**
 * 玩家相机输入事件，由客户端发送给服务端，用于同步鼠标移动（视角变化）
 */
@Getter
public class PlayerCameraInputEvent extends GameEvent {
    private final float deltaYaw;
    private final float deltaPitch;
    
    public PlayerCameraInputEvent(float deltaYaw, float deltaPitch) {
        this.deltaYaw = deltaYaw;
        this.deltaPitch = deltaPitch;
    }
}


