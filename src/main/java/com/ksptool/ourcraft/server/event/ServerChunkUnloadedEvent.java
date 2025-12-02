package com.ksptool.ourcraft.server.event;

import com.ksptool.ourcraft.server.world.chunk.FlexServerChunk;
import com.ksptool.ourcraft.sharedcore.world.WorldEvent;

import lombok.Getter;

/**
 * 当 FlexServerChunk 完成卸载并存盘后触发
 */
@Getter
public class ServerChunkUnloadedEvent implements WorldEvent {

    private final FlexServerChunk chunk;

    public ServerChunkUnloadedEvent(FlexServerChunk chunk) {
        this.chunk = chunk;
    }

}

