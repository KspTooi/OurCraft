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

    @Override
    public void action(double delta, SharedWorld world) {

        

        throw new UnsupportedOperationException("Unimplemented method 'action'");
    }



    /**
     * 更新玩家的区块令牌（移动视口）
     * 计算新旧视口差异，只增删必要的令牌
     *
     * @param playerSessionId 玩家SessionID
     * @param viewDistance 视距（半径）
     */
    public void updatePlayerTokens(long playerSessionId, ChunkPos oldChunPos, ChunkPos newChunPos, int viewDistance) {
        
        Set<ChunkPos> newViewport = new HashSet<>();
        
        //计算新视口的所有区块坐标
        for (int x = newChunPos.getX() - viewDistance; x <= newChunPos.getX() + viewDistance; x++) {
            for (int z = newChunPos.getZ() - viewDistance; z <= newChunPos.getZ() + viewDistance; z++) {
                newViewport.add(ChunkPos.of(x, z));
            }
        }

        //如果没有旧坐标（玩家刚加入），直接全部添加
        if (oldChunPos == null) {
            for (ChunkPos pos : newViewport) {
                addTokenForPlayer(playerSessionId, pos);
            }
            return;
        }
        
        //如果位置没变，直接返回
        if (oldChunPos.equals(newChunPos)) {
             // 视距可能变了，简单起见这里假设视距不变，或者全量重新计算也行
             return;
        }

        Set<ChunkPos> oldViewport = new HashSet<>();
        //计算旧视口的所有区块坐标
        for (int x = oldChunPos.getX() - viewDistance; x <= oldChunPos.getX() + viewDistance; x++) {
            for (int z = oldChunPos.getZ() - viewDistance; z <= oldChunPos.getZ() + viewDistance; z++) {
                oldViewport.add(ChunkPos.of(x, z));
            }
        }

        //找出需要新添加的 (在 new 中但不在 old 中)
        for (ChunkPos pos : newViewport) {
            if (!oldViewport.contains(pos)) {
                addTokenForPlayer(playerSessionId, pos);
            }
        }

        //找出需要移除的 (在 old 中但不在 new 中)
        for (ChunkPos pos : oldViewport) {
            if (!newViewport.contains(pos)) {
                removeTokenForPlayer(playerSessionId, pos);
            }
        }
    }




}
