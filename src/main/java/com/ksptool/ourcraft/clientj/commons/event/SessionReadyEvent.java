package com.ksptool.ourcraft.clientj.commons.event;

import com.ksptool.ourcraft.clientj.commons.ClientEvent;

import lombok.Getter;

/**
 * 网络会话准备就绪事件
 * 用于通知客户端网络会话准备就绪
 * 
 * 网络阶段
 * 1.建立TCP
 * 2.处理认证
 * 3.接收批数据
 * 4.通知服务端批数据处理完成
 */
@Getter
public class SessionReadyEvent implements ClientEvent{
    
}
