package com.ksptool.ourcraft.clientj.commons.event;

import com.ksptool.ourcraft.clientj.commons.ClientEvent;
import com.ksptool.ourcraft.clientj.network.ClientNetworkSession;
import com.ksptool.ourcraft.sharedcore.network.nvo.PsPlayerNVo;
import lombok.Getter;

@Getter
public class ProcessSwitchPlayerEvent implements ClientEvent {
    private final ClientNetworkSession session;
    private final PsPlayerNVo vo;

    public ProcessSwitchPlayerEvent(ClientNetworkSession session, PsPlayerNVo vo) {
        this.session = session;
        this.vo = vo;
    }

    public static ProcessSwitchPlayerEvent of(ClientNetworkSession session, PsPlayerNVo vo) {
        return new ProcessSwitchPlayerEvent(session, vo);
    }
}


