package com.ksptool.ourcraft.clientj.network;

import com.ksptool.ourcraft.clientj.commons.event.ProcessSwitchEvent;
import com.ksptool.ourcraft.clientj.service.ClientEventService;
import com.ksptool.ourcraft.clientj.service.ClientNetworkService;
import com.ksptool.ourcraft.sharedcore.network.nvo.PsNVo;

import lombok.extern.slf4j.Slf4j;

/**
 * 网络处理器
 * 用于处理网络数据包 通常负责把网络包转换为事件并发布到事件服务
 */
@Slf4j
public class NetworkHandler {

    private final ClientEventService ces;
    
    private final ClientNetworkService cns;

    public NetworkHandler(ClientNetworkService cns) {
        this.cns = cns;
        this.ces = cns.getCes();
    }

    /**
     * 处理进程切换
     * @param session 网络会话
     * @param vo 进程切换数据包
     */
    public void onProcessSwitch(ClientNetworkSession session,PsNVo vo){
        ces.publish(ProcessSwitchEvent.of(session, vo));
    }

}
