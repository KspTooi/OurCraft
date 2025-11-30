package com.ksptool.ourcraft.server.event;

import com.ksptool.ourcraft.sharedcore.world.WorldEvent;

import lombok.Getter;

/**
 * 服务端玩家输入事件
 * 用于传递玩家的移动和跳跃输入
 */
@Getter
public class ServerPlayerInputEvent implements WorldEvent{

    private final long sessionId;
    private final boolean w;
    private final boolean s;
    private final boolean a;
    private final boolean d;
    private final boolean space;
    private final boolean shift;

    public ServerPlayerInputEvent(long sessionId, boolean w, boolean s, boolean a, boolean d, boolean space, boolean shift, float yaw, float pitch) {
        this.sessionId = sessionId;
        this.w = w;
        this.s = s;
        this.a = a;
        this.d = d;
        this.space = space;
        this.shift = shift;
    }
}
