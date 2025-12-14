package com.ksptool.ourcraft.clientj.commons.event;

import com.ksptool.ourcraft.clientj.commons.ClientEvent;
import com.ksptool.ourcraft.clientj.network.ClientNetworkSession;
import com.ksptool.ourcraft.sharedcore.network.nvo.HuChunkNVo;
import com.ksptool.ourcraft.sharedcore.utils.position.ChunkPos;
import lombok.Getter;

/**
 * 区块加载事件(Chunk Load Event)
 * 当客户端收到服务端反馈区块加载事件时触发
 */
@Getter
public class ChunkLoadEvent implements ClientEvent {

    //网络会话
    private final ClientNetworkSession session;

    //区块坐标
    private final ChunkPos chunkPos;

    //未序列化的区块数据
    private final byte[] blockData;

    public ChunkLoadEvent(ClientNetworkSession session, ChunkPos chunkPos, byte[] blockData) {
        this.session = session;
        this.chunkPos = chunkPos;
        this.blockData = blockData;
    }

    public static ChunkLoadEvent of(ClientNetworkSession session, ChunkPos chunkPos, byte[] blockData) {
        return new ChunkLoadEvent(session, chunkPos, blockData);
    }
    
    public static ChunkLoadEvent of(ClientNetworkSession session, HuChunkNVo chunkNVo) {
        return new ChunkLoadEvent(session, ChunkPos.of(chunkNVo.chunkX(), chunkNVo.chunkZ()), chunkNVo.blockData());
    }
}
