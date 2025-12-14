package com.ksptool.ourcraft.clientj.commons.event;


import com.ksptool.ourcraft.clientj.commons.ClientEvent;
import lombok.Getter;

/**
 * 网络会话关闭事件
 * 用于通知客户端网络会话关闭
 */
@Getter
public class SessionCloseEvent implements ClientEvent{

    private final String reason;

    public SessionCloseEvent(String reason) {
        this.reason = reason;
    }
    
}
