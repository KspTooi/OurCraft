package com.ksptool.ourcraft.server.event;

import com.ksptool.ourcraft.server.world.chunk.FlexServerChunk;
import com.ksptool.ourcraft.sharedcore.world.WorldEvent;

import lombok.Getter;

/**
 * 当 FlexServerChunk 完成加载或生成，并准备好被使用时触发
 */
@Getter
public class ServerChunkReadyEvent implements WorldEvent {

    private final FlexServerChunk chunk;

    public ServerChunkReadyEvent(FlexServerChunk chunk) {
        this.chunk = chunk;
    }

}
