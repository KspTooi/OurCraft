package com.ksptool.ourcraft.server.network;


import com.ksptool.ourcraft.server.event.ServerPlayerInputEvent;
import com.ksptool.ourcraft.sharedcore.network.packets.ClientKeepAliveNDto;
import com.ksptool.ourcraft.sharedcore.network.packets.PlayerInputStateNDto;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClientNetworkHandler {


    public void playerInput(NetworkSession session, PlayerInputStateNDto dto){
        var world = session.getWorld();
        if(world == null){
            log.error("会话:{} 无法处理玩家输入事件,玩家所在世界不存在", session.getId());
            return;
        }
        world.getSweb().publish(new ServerPlayerInputEvent(session.getId(), dto.w(), dto.s(), dto.a(), dto.d(), dto.space(), dto.shift(), dto.yaw(), dto.pitch()));
    }


    public void clientKeepAlive(NetworkSession session, ClientKeepAliveNDto dto){
        session.updateHeartbeat();
    }

}
