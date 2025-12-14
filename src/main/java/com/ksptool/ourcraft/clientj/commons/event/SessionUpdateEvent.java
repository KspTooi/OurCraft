package com.ksptool.ourcraft.clientj.commons.event;

import com.ksptool.ourcraft.clientj.commons.ClientEvent;
import com.ksptool.ourcraft.clientj.network.ClientNetworkSession;
import lombok.Getter;

/**
 * 网络会话状态更新事件
 * 用于通知客户端网络会话状态更新
 */
@Getter
public class SessionUpdateEvent implements ClientEvent{
    
    //网络会话
    private final ClientNetworkSession session;

    //网络会话阶段
    private final ClientNetworkSession.Stage newStage;

    //更新文本
    private final String text;

    public SessionUpdateEvent(ClientNetworkSession session, ClientNetworkSession.Stage newStage, String text) {
        this.session = session;
        this.newStage = newStage;
        this.text = text;
    }

    public static SessionUpdateEvent of(ClientNetworkSession session, ClientNetworkSession.Stage newStage, String text) {
        return new SessionUpdateEvent(session, newStage, text);
    }

}
