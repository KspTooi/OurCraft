package com.ksptool.ourcraft.clientj.commons.event;

import org.xml.sax.SAXParseException;

import com.ksptool.ourcraft.clientj.commons.ClientEvent;
import com.ksptool.ourcraft.clientj.network.ClientNetworkSession;
import com.ksptool.ourcraft.sharedcore.network.nvo.PsChunkNVo;
import com.ksptool.ourcraft.sharedcore.utils.position.ChunkPos;
import lombok.Getter;

/**
 * 当收到服务器发送的区块数据时触发的事件
 */
@Getter
public class PsChunkRcvEvent implements ClientEvent {

    //网络会话
    private final ClientNetworkSession session;

    //区块坐标
    private final ChunkPos chunkPos;

    //未序列化的区块数据
    private final byte[] blockData;

    public PsChunkRcvEvent(ClientNetworkSession session, ChunkPos chunkPos, byte[] blockData) {
        this.session = session;
        this.chunkPos = chunkPos;
        this.blockData = blockData;
    }

    public static PsChunkRcvEvent of(ClientNetworkSession session, ChunkPos chunkPos, byte[] blockData) {
        return new PsChunkRcvEvent(session, chunkPos, blockData);
    }
    
    public static PsChunkRcvEvent of(ClientNetworkSession session, PsChunkNVo chunkNVo) {
        return new PsChunkRcvEvent(session, ChunkPos.of(chunkNVo.chunkX(), chunkNVo.chunkZ()), chunkNVo.blockData());
    }
}

