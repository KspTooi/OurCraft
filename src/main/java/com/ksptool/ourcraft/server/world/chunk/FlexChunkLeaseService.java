package com.ksptool.ourcraft.server.world.chunk;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.HashSet;
import java.util.Set;

import com.ksptool.ourcraft.server.world.ServerWorld;
import com.ksptool.ourcraft.sharedcore.utils.position.ChunkPos;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * 区块令牌服务，负责管理区块的令牌
 */

@Slf4j
public class FlexChunkLeaseService {

    //全部区块令牌
    private final List<FlexChunkLease> tokens = new CopyOnWriteArrayList<>();

    @Getter
    private final ServerWorld world;

    public FlexChunkLeaseService(ServerWorld world) {
        this.world = world;
    }

    /**
     * 添加区块令牌
     * @param playerSessionId 玩家SessionID
     * @param chunkPos 区块坐标
     */
    public void addTokenForPlayer(long playerSessionId, ChunkPos chunkPos) {
        //var token = new FlexChunkLease(chunkPos, FlexChunkLease.HolderType.PLAYER, playerSessionId, -1, 10);

        //检查是否已有同坐标的令牌(同一个玩家不能拥有同一个区块的多个令牌)
        /*boolean alreadyHasToken = tokens.stream()
        .anyMatch(t -> t.getChunkPos().equals(chunkPos) 
                  && t.getHolderType() == FlexChunkLease.HolderType.PLAYER
                  && t.getOwnerSessionId() == playerSessionId);
    
        if (alreadyHasToken) {
            return; // 这个玩家已经拥有该区块的 Token 了，无需重复添加
        }*/

        //tokens.add(token);
    }

    /**
     * 移除区块令牌
     * @param playerSessionId 玩家SessionID
     * @param chunkPos 区块坐标
     */
    public void removeTokenForPlayer(long playerSessionId, ChunkPos chunkPos) {
        //tokens.removeIf(token -> token.getChunkPos().equals(chunkPos) && token.getOwnerSessionId() == playerSessionId);
    }
    
    /**
     * 获取区块令牌的优先级
     * @param chunkPos 区块坐标
     * @return 优先级 优先级最高的令牌将被返回 如果没有任何令牌则返回0
     */
    public int getTokenPriority(ChunkPos chunkPos) {
        return 1;
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
