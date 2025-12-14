package com.ksptool.ourcraft.clientj.commons.event;

import com.ksptool.ourcraft.clientj.commons.ClientEvent;
import com.ksptool.ourcraft.clientj.network.ClientNetworkSession;
import com.ksptool.ourcraft.sharedcore.network.nvo.HuPlayerLocationNVo;

import lombok.Getter;

/**
 * 玩家位置更新事件(Player Location Update Event)
 * 当服务端要求玩家位置调整时触发
 */
@Getter
public class PlayerLocationUpdateEvent implements ClientEvent {

    //网络会话
    private final ClientNetworkSession session;

    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    public PlayerLocationUpdateEvent(ClientNetworkSession session, double x, double y, double z, float yaw, float pitch) {
        this.session = session;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public static PlayerLocationUpdateEvent of(ClientNetworkSession session, HuPlayerLocationNVo vo) {
        return new PlayerLocationUpdateEvent(session, vo.x(), vo.y(), vo.z(), vo.yaw(), vo.pitch());
    }

}
