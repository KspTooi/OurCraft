package com.ksptool.ourcraft.clientj.commons.event;

import com.ksptool.ourcraft.clientj.commons.ClientEvent;
import com.ksptool.ourcraft.clientj.network.ClientNetworkSession;
import com.ksptool.ourcraft.sharedcore.network.nvo.PsPlayerNVo;
import lombok.Getter;

/**
 * 当进程切换时接收玩家网络包时触发的事件
 */
@Getter
public class PsPlayerRcvEvent implements ClientEvent {

    //网络会话
    private final ClientNetworkSession session;

    //玩家UUID
    private final String uuid;

    //玩家名称
    private final String name;

    //玩家血量
    private final int health;

    //玩家饥饿度
    private final int hungry;

    //玩家位置X
    private final double posX;

    //玩家位置Y
    private final double posY;

    //玩家位置Z
    private final double posZ;

    //玩家朝向Yaw
    private final double yaw;

    //玩家朝向Pitch
    private final double pitch;

    //玩家地面加速度
    private final float ga;

    //玩家空中加速度
    private final float aa;

    //玩家最大移动速度
    private final float ms;

    public PsPlayerRcvEvent(ClientNetworkSession session, String uuid, String name, int health, int hungry, double posX, double posY, double posZ, double yaw, double pitch, float ga, float aa, float ms) {
        this.session = session;
        this.uuid = uuid;
        this.name = name;
        this.health = health;
        this.hungry = hungry;
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.yaw = yaw;
        this.pitch = pitch;
        this.ga = ga;
        this.aa = aa;
        this.ms = ms;
    }

    public static PsPlayerRcvEvent of(ClientNetworkSession session, PsPlayerNVo vo) {
        return new PsPlayerRcvEvent(session, vo.uuid(), vo.name(), vo.health(), vo.hungry(), vo.posX(), vo.posY(), vo.posZ(), vo.yaw(), vo.pitch(), vo.ga(), vo.aa(), vo.ms());
    }
}


