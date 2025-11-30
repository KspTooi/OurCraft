package com.ksptool.ourcraft.server.world.chunk;

import com.ksptool.ourcraft.sharedcore.utils.position.ChunkPos;

import lombok.Getter;

/**
 * 区块令牌，负责管理区块的令牌
 */
@Getter
public class FlexChunkToken {

    /**
     * 持有人类型
     */
    public enum OwnerKind {
        PLAYER,  //玩家持有
        SERVER,  //服务器持有令牌
    }

    //持有人类型
    private final OwnerKind ownerKind;

    //持有人SessionID(用于玩家)
    private final long ownerSessionId;

    //生存时间 当生存时间小于1时令牌将会被销毁(为-1时表示永不过期)
    private final int ttl;

    //优先级
    private final int priority;

    //区块坐标
    private final ChunkPos chunkPos;

    public FlexChunkToken(ChunkPos chunkPos,OwnerKind ownerKind, long ownerSessionId, int ttl, int priority) {
        this.chunkPos = chunkPos;
        this.ownerKind = ownerKind;
        this.ownerSessionId = ownerSessionId;
        this.ttl = ttl;
        this.priority = priority;
    }


}
