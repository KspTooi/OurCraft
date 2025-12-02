package com.ksptool.ourcraft.server.event;

import com.ksptool.ourcraft.server.world.chunk.FlexChunkLease;
import com.ksptool.ourcraft.server.world.chunk.FlexChunkLeaseService;
import com.ksptool.ourcraft.sharedcore.utils.position.ChunkPos;
import com.ksptool.ourcraft.sharedcore.world.WorldEvent;

import lombok.Getter;

/**
 * 当区块租约签发时触发
 */
@Getter
public class ServerChunkLeaseIssuedEvent implements WorldEvent {

    private final ChunkPos chunkPos;

    private final FlexChunkLease lease;

    private final FlexChunkLeaseService fcls;
    
    public ServerChunkLeaseIssuedEvent(ChunkPos chunkPos, FlexChunkLease lease, FlexChunkLeaseService fcls) {
        this.chunkPos = chunkPos;
        this.lease = lease;
        this.fcls = fcls;
    }

}
