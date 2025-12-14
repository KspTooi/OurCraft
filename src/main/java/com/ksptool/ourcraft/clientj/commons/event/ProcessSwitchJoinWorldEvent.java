package com.ksptool.ourcraft.clientj.commons.event;

import com.ksptool.ourcraft.clientj.commons.ClientEvent;
import com.ksptool.ourcraft.clientj.network.ClientNetworkSession;
import com.ksptool.ourcraft.sharedcore.network.nvo.PsJoinWorldNVo;
import lombok.Getter;

@Getter
public class ProcessSwitchJoinWorldEvent implements ClientEvent {
    private final ClientNetworkSession session;
    private final PsJoinWorldNVo vo;

    public ProcessSwitchJoinWorldEvent(ClientNetworkSession session, PsJoinWorldNVo vo) {
        this.session = session;
        this.vo = vo;
    }

    public static ProcessSwitchJoinWorldEvent of(ClientNetworkSession session, PsJoinWorldNVo vo) {
        return new ProcessSwitchJoinWorldEvent(session, vo);
    }
}


