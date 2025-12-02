package com.ksptool.ourcraft.server.world.chunk;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.HashSet;
import java.util.Set;
import com.ksptool.ourcraft.server.world.ServerWorld;
import com.ksptool.ourcraft.sharedcore.utils.position.ChunkPos;
import com.ksptool.ourcraft.sharedcore.world.SequenceUpdate;
import com.ksptool.ourcraft.sharedcore.world.SharedWorld;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * 区块租约服务，负责管理区块的租约
 */
@Slf4j
public class FlexChunkLeaseService implements SequenceUpdate{

    //区块坐标->区块租约
    private final Map<ChunkPos, FlexChunkLease> chunkLeases = new ConcurrentHashMap<>();

    @Getter
    private final ServerWorld world;

    public FlexChunkLeaseService(ServerWorld world) {
        this.world = world;
    }

    /**
     * 签发租约(此函数签发服务器级别永久租约)
     * @param chunkPos 区块坐标
     * @param lease 租约
     */
    public void issueLease(ChunkPos chunkPos) {
        var lease = chunkLeases.computeIfAbsent(chunkPos, k -> FlexChunkLease.ofHigh(k, FlexChunkLease.HolderType.SERVER, -1));
        lease.getPermanent().set(true);
    }

    /**
     * 签发租约(此函数签发Player级别租约,如果已有租约则续租)
     * @param chunkPos 区块坐标
     * @param playerSessionId 玩家SessionID
     */
    public void issueLease(ChunkPos chunkPos, long playerSessionId) {

        if(chunkPos == null || playerSessionId == -1){
            throw new IllegalArgumentException("无法签发租约: 区块坐标或SessionID不能为空");
        }

        var lease = chunkLeases.computeIfAbsent(chunkPos, k -> FlexChunkLease.ofHigh(k, FlexChunkLease.HolderType.PLAYER, playerSessionId));
        lease.getPermanent().set(false);
        lease.renew(world.getTemplate().getMaxPlayerChunkLeaseAction());
    }

    /**
     * 执行更新
     * @param delta 距离上一Action经过的时间（秒）由SWEU传入
     * @param world 世界
     */
    @Override
    public void action(double delta, SharedWorld world) {

        
        throw new UnsupportedOperationException("Unimplemented method 'action'");
    }





}
