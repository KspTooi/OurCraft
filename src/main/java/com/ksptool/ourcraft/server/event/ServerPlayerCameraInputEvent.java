package com.ksptool.ourcraft.server.event;

import com.ksptool.ourcraft.sharedcore.world.WorldEvent;

import lombok.Getter;

/**
 * 服务端玩家相机输入事件
 * 客户端在 GameClient.java 中采集鼠标移动，并直接更新本地玩家朝向。发送给服务端的网络包中包含的是计算后的绝对角度。
 */
@Getter
public class ServerPlayerCameraInputEvent implements WorldEvent {

    //玩家会话ID
    private final long sessionId;

    //鼠标水平移动量
    private final float deltaYaw;

    //鼠标垂直移动量
    private final float deltaPitch;

    public ServerPlayerCameraInputEvent(long sessionId, float deltaYaw, float deltaPitch) {
        this.sessionId = sessionId;
        this.deltaYaw = deltaYaw;
        this.deltaPitch = deltaPitch;
    }

}
