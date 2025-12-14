package com.ksptool.ourcraft.clientj.commons.event;

import com.ksptool.ourcraft.clientj.commons.ClientEvent;
import com.ksptool.ourcraft.clientj.network.ClientNetworkSession;
import com.ksptool.ourcraft.sharedcore.network.nvo.HuChunkUnloadNVo;
import com.ksptool.ourcraft.sharedcore.utils.position.ChunkPos;
import lombok.Getter;

@Getter
public class ChunkUnloadEvent implements ClientEvent {

    //网络会话
    private final ClientNetworkSession session;

    //区块坐标
    private final ChunkPos chunkPos;

    public ChunkUnloadEvent(ClientNetworkSession session, ChunkPos chunkPos) {
        this.session = session;
        this.chunkPos = chunkPos;
    }

    public static ChunkUnloadEvent of(ClientNetworkSession session, ChunkPos chunkPos) {
        return new ChunkUnloadEvent(session, chunkPos);
    }

    public static ChunkUnloadEvent of(ClientNetworkSession session, HuChunkUnloadNVo vo) {
        return new ChunkUnloadEvent(session, vo.pos());
    }
}
