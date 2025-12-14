package com.ksptool.ourcraft.clientj.commons.event;

import com.ksptool.ourcraft.clientj.commons.ClientEvent;
import com.ksptool.ourcraft.clientj.network.ClientNetworkSession;
import com.ksptool.ourcraft.sharedcore.network.nvo.PsJoinWorldNVo;
import lombok.Getter;

/**
 * 当服务端已经将玩家投入世界后产生的网络事件
 */
@Getter
public class PsJoinWorldEvent implements ClientEvent {

    //网络会话
    private final ClientNetworkSession session;

    public PsJoinWorldEvent(ClientNetworkSession session) {
        this.session = session;
    }

    public static PsJoinWorldEvent of(ClientNetworkSession session) {
        return new PsJoinWorldEvent(session);
    }
}


