package com.ksptool.ourcraft.clientj.network;

import com.ksptool.ourcraft.clientj.commons.event.ChunkLoadEvent;
import com.ksptool.ourcraft.clientj.commons.event.ChunkUnloadEvent;
import com.ksptool.ourcraft.clientj.commons.event.PlayerLocationUpdateEvent;
import com.ksptool.ourcraft.clientj.commons.event.PsChunkRcvEvent;
import com.ksptool.ourcraft.clientj.commons.event.PsEvent;
import com.ksptool.ourcraft.clientj.commons.event.PsJoinWorldEvent;
import com.ksptool.ourcraft.clientj.commons.event.PsPlayerRcvEvent;
import com.ksptool.ourcraft.clientj.service.ClientEventService;
import com.ksptool.ourcraft.clientj.service.ClientNetworkService;
import com.ksptool.ourcraft.sharedcore.network.nvo.HuChunkNVo;
import com.ksptool.ourcraft.sharedcore.network.nvo.HuChunkUnloadNVo;
import com.ksptool.ourcraft.sharedcore.network.nvo.HuPlayerLocationNVo;
import com.ksptool.ourcraft.sharedcore.network.nvo.PsChunkNVo;
import com.ksptool.ourcraft.sharedcore.network.nvo.PsJoinWorldNVo;
import com.ksptool.ourcraft.sharedcore.network.nvo.PsNVo;
import com.ksptool.ourcraft.sharedcore.network.nvo.PsPlayerNVo;

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
     * 处理服务端要求进程切换网络包
     * @param session 网络会话
     * @param vo 进程切换数据包
     */
    public void onPsEvent(ClientNetworkSession session, PsNVo vo){
        ces.publish(PsEvent.of(session, vo));
    }

    /**
     * 处理接收进程切换落地区块网络包
     * @param session 网络会话
     * @param vo 区块数据包
     */
    public void onPsChunkRcv(ClientNetworkSession session, PsChunkNVo vo){
        ces.publish(PsChunkRcvEvent.of(session, vo));
    }

    /**
     * 处理接收进程切换玩家网络包
     * @param session 网络会话
     * @param vo 玩家数据包
     */
    public void onPsPlayerRcv(ClientNetworkSession session, PsPlayerNVo vo){
        ces.publish(PsPlayerRcvEvent.of(session, vo));
    }

    /**
     * 处理服务端已经将玩家投入世界后产生的网络事件
     * @param session 网络会话
     * @param vo 加入世界数据包
     */
    public void onPsJoinWorldRcv(ClientNetworkSession session, PsJoinWorldNVo vo){
        ces.publish(PsJoinWorldEvent.of(session));
    }

    
    /**
     * 处理接收服务端反馈玩家位置网络包
     * @param session 网络会话
     * @param vo 玩家位置反馈数据包
     */
    public void onPlayerLocationUpdate(ClientNetworkSession session, HuPlayerLocationNVo vo){
        ces.publish(PlayerLocationUpdateEvent.of(session, vo));
    }

    /**
     * 处理接收服务端反馈区块加载网络包
     * @param session 网络会话
     * @param vo 区块加载数据包
     */
    public void onChunkLoad(ClientNetworkSession session, HuChunkNVo vo){
        ces.publish(ChunkLoadEvent.of(session, vo));
    }

    /**
     * 处理接收服务端反馈区块卸载网络包
     * @param session 网络会话
     * @param vo 区块卸载数据包
     */
    public void onChunkUnload(ClientNetworkSession session, HuChunkUnloadNVo vo){
        ces.publish(ChunkUnloadEvent.of(session, vo));
    }

}
