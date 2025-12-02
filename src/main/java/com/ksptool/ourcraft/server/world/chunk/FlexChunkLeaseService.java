package com.ksptool.ourcraft.server.world.chunk;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.ksptool.ourcraft.server.entity.ServerPlayer;
import com.ksptool.ourcraft.server.event.ServerChunkLeaseExpiredEvent;
import com.ksptool.ourcraft.server.event.ServerChunkLeaseIssuedEvent;
import com.ksptool.ourcraft.server.world.ServerWorld;
import com.ksptool.ourcraft.server.world.ServerWorldEventBus;
import com.ksptool.ourcraft.server.world.SimpleEntityService;
import com.ksptool.ourcraft.sharedcore.utils.position.ChunkPos;
import com.ksptool.ourcraft.sharedcore.utils.viewport.ChunkViewPort;
import com.ksptool.ourcraft.sharedcore.world.SequenceUpdate;
import com.ksptool.ourcraft.sharedcore.world.SharedWorld;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * 区块租约服务，负责管理区块的租约
 */
@Slf4j
public class FlexChunkLeaseService implements SequenceUpdate{

    //区块坐标->区块租约集合
    private final Map<ChunkPos, Set<FlexChunkLease>> chunkLeasesMap = new ConcurrentHashMap<>();

    //Player SessionID->该玩家持有的所有区块坐标集合(用于快速查找)
    private final Map<Long, Set<ChunkPos>> playerLeaseMap = new ConcurrentHashMap<>();

    @Getter
    private final ServerWorld world;

    private final SimpleEntityService ses;

    private final ServerWorldEventBus sweb;

    public FlexChunkLeaseService(ServerWorld world) {
        this.world = world;
        this.ses = world.getSes();
        this.sweb = world.getEb();
    }

    /**
     * 签发租约(此函数签发服务器级别永久租约)
     * @param chunkPos 区块坐标
     * @param lease 租约
     */
    public void issueLease(ChunkPos chunkPos) {
        var leases = chunkLeasesMap.computeIfAbsent(chunkPos, k -> ConcurrentHashMap.newKeySet());
        var lease = FlexChunkLease.ofHigh(chunkPos, FlexChunkLease.HolderType.SERVER, -1);
        lease.getPermanent().set(true);
        
        if(leases.add(lease)){
            //发布ServerChunkLeaseIssuedEvent事件
            sweb.publish(new ServerChunkLeaseIssuedEvent(chunkPos, lease, this));
            log.debug("签发服务器级别永久租约 区块:{} 等级:{}", chunkPos, lease.getLevel());
        }
    }

    /**
     * 签发永久租约(此函数签发Player级别永久租约)
     * @param chunkPos 区块坐标
     * @param playerSessionId Player SessionID
     */
    public void issuePermanentLease(ChunkPos chunkPos, long playerSessionId) {

        if(chunkPos == null || playerSessionId == -1){
            throw new IllegalArgumentException("无法签发租约: 区块坐标或SessionID不能为空");
        }

        var leases = chunkLeasesMap.computeIfAbsent(chunkPos, k -> ConcurrentHashMap.newKeySet());
        
        //检查是否已存在该玩家的永久租约
        for(var existingLease : leases){
            if(existingLease.isPlayer() && existingLease.getHolderId() == playerSessionId && existingLease.isPermanent()){
                return;
            }
        }

        //创建新的永久租约
        var newLease = FlexChunkLease.ofHigh(chunkPos, FlexChunkLease.HolderType.PLAYER, playerSessionId);
        newLease.setPermanent(true);
        newLease.renew(world.getTemplate().getMaxPlayerChunkLeaseAction());
        
        if(leases.add(newLease)){
            //将新的租约添加到玩家租约池中并发布ServerChunkLeaseIssuedEvent事件
            playerLeaseMap.computeIfAbsent(playerSessionId, k -> ConcurrentHashMap.newKeySet()).add(chunkPos);
            sweb.publish(new ServerChunkLeaseIssuedEvent(chunkPos, newLease, this));
            log.debug("签发玩家级别永久租约 区块:{} 玩家SessionID:{} 等级:{}", chunkPos, playerSessionId, newLease.getLevel());
        }
    }


    /**
     * 吊销永久租约(此函数吊销Player级别永久租约)
     * @param chunkPos 区块坐标
     * @param playerSessionId 玩家SessionID
     */
    public void revokePermanentLease(ChunkPos chunkPos, long playerSessionId) {
        var leases = chunkLeasesMap.get(chunkPos);
        if(leases == null){
            return;
        }
        
        FlexChunkLease revokedLease = null;
        for(var lease : leases){
            if(!lease.isPlayer() || lease.getHolderId() != playerSessionId){
                continue;
            }
            if(!lease.isPermanent()){
                continue;
            }
            revokedLease = lease;
            lease.setPermanent(false);
            lease.renew(world.getTemplate().getMaxPlayerChunkLeaseAction());
            break;
        }
        
        if(revokedLease == null){
            return;
        }
        
        log.debug("吊销玩家级别永久租约 区块:{} 玩家SessionID:{} 等级:{}", chunkPos, playerSessionId, revokedLease.getLevel());
        
        //如果该区块没有租约了，移除该区块的租约集合
        if(leases.isEmpty()){
            chunkLeasesMap.remove(chunkPos);
        }
        
        //同步更新playerLeaseMap
        var playerLeases = playerLeaseMap.get(playerSessionId);
        if(playerLeases != null){
            //检查该区块是否还有其他该玩家的租约
            var remainingLeases = chunkLeasesMap.get(chunkPos);
            boolean hasOtherLease = remainingLeases != null && remainingLeases.stream()
                .anyMatch(lease -> lease.isPlayer() && lease.getHolderId() == playerSessionId);
            if(!hasOtherLease){
                playerLeases.remove(chunkPos);
                if(playerLeases.isEmpty()){
                    playerLeaseMap.remove(playerSessionId);
                }
            }
        }
    }


    /**
     * 获取租约等级
     * @param chunkPos 区块坐标
     * @return 租约等级 如果这个ChunkPos不存在任何租约则返回null，返回最高等级的租约等级
     */
    public FlexChunkLease.Level getLeaseLevel(ChunkPos chunkPos){
        var leases = chunkLeasesMap.get(chunkPos);
        if(leases == null || leases.isEmpty()){
            return null;
        }
        
        return leases.stream()
            .filter(lease -> !lease.isExpired())
            .map(FlexChunkLease::getLevel)
            .max((l1, l2) -> Integer.compare(l1.getValue(), l2.getValue()))
            .orElse(null);
    }

    /**
     * 获取租约
     * @param chunkPos 区块坐标
     * @return 租约 如果这个ChunkPos不存在任何租约则返回null，返回最高等级的租约
     */
    public FlexChunkLease getLease(ChunkPos chunkPos){
        var leases = chunkLeasesMap.get(chunkPos);
        if(leases == null || leases.isEmpty()){
            return null;
        }
        
        return leases.stream()
            .filter(lease -> !lease.isExpired())
            .max((l1, l2) -> Integer.compare(l1.getLevel().getValue(), l2.getLevel().getValue()))
            .orElse(null);
    }

    /**
     * 统计该位置有效的剩余租约数量
     * @param chunkPos 区块坐标
     * @return 剩余租约数量
     */
    public long getRemainingLeaseCount(ChunkPos chunkPos){
        var leases = chunkLeasesMap.get(chunkPos);
        if(leases == null){
            return 0;
        }
        return leases.stream().filter(lease -> !lease.isExpired()).count();
    }


    /**
     * 执行更新
     * 
     * 运作原理：
     * 1. 玩家租约管理阶段：
     *    - 遍历所有实体，筛选出ServerPlayer
     *    - 对于未初始化租约的玩家：根据当前区块坐标和视距计算视口范围，为视口内所有区块签发永久租约
     *    - 对于已初始化租约的玩家：检测玩家是否移动到了新区块
     *      * 如果移动了，计算新的视口范围
     *      * 遍历玩家当前持有的所有租约区块，如果区块不在新视口内，则吊销该区块的永久租约
     *      * 遍历新视口内的所有区块，如果该区块不存在该玩家的永久租约，则签发新的永久租约
     * 
     * 2. 租约TTL更新与过期清理阶段：
     *    - 遍历所有区块的所有租约，调用每个租约的action方法扣减TTL（永久租约不会扣减）
     *    - 收集所有过期的租约到临时列表（避免在遍历时修改集合）
     *    - 遍历过期租约列表，执行清理操作：
     *      * 从chunkLeasesMap中移除过期租约，如果该区块的租约集合为空则移除整个集合
     *      * 如果是玩家租约，检查该区块是否还有其他该玩家的租约，如果没有则从playerLeaseMap中移除该区块引用
     *      * 发布ServerChunkLeaseExpiredEvent事件，通知其他系统租约已过期
     * 
     * @param delta 距离上一Action经过的时间（秒）由SWEU传入
     * @param world 世界
     */
    @Override
    public void action(double delta, SharedWorld world) {

        var entities = ses.getEntities();
        
        //第一阶段：玩家租约管理
        for(var entity : entities){
            if(!(entity instanceof ServerPlayer)){
                continue;
            }
            var p = (ServerPlayer) entity;

            //如果未完成租约初始化,则需要初始化租约
            if(!p.isLeaseInited()){
                var vp = ChunkViewPort.of(p.getCurrentChunkPos(), p.getViewDistance());
                for(var vPos : vp.getChunkPosSet()){
                    issuePermanentLease(vPos, p.getSessionId());
                }
                p.markLeaseInited();
                continue;
            }

            //已经完成租约初始化,则需要更新租约,先判断Player有没有离开当前区块
            if(!p.getCurrentChunkPos().equals(p.getPreviousChunkPos())){
                var newViewport = ChunkViewPort.of(p.getCurrentChunkPos(), p.getViewDistance());
                var newViewportChunks = newViewport.getChunkPosSet();

                //吊销不在新视口内的区块租约
                var playerLeases = playerLeaseMap.get(p.getSessionId());
                if(playerLeases != null){
                    for(var leaseChunkPos : playerLeases){
                        if(!newViewport.contains(leaseChunkPos)){
                            revokePermanentLease(leaseChunkPos, p.getSessionId());
                        }
                    }
                }

                //为新视口内的区块签发租约（如果不存在）
                for(var chunkPos : newViewportChunks){
                    var leases = chunkLeasesMap.get(chunkPos);
                    boolean hasPermanentLease = leases != null && leases.stream()
                        .anyMatch(lease -> lease.isPlayer() && lease.getHolderId() == p.getSessionId() && lease.isPermanent());
                    if(!hasPermanentLease){
                        issuePermanentLease(chunkPos, p.getSessionId());
                    }
                }
            }

        }


        //第二阶段：扣减租约TTL并移除过期租约
        var expiredLeases = new java.util.ArrayList<FlexChunkLease>();
        //遍历所有租约，扣减TTL并收集过期租约
        for(var leases : chunkLeasesMap.values()){
            for(var lease : leases){
                lease.action(delta, world);
                if(lease.isExpired()){
                    expiredLeases.add(lease);
                }
            }
        }
        
        //清理过期租约
        for(var lease : expiredLeases){
            var leases = chunkLeasesMap.get(lease.getChunkPos());
            if(leases != null){
                leases.remove(lease);
                if(leases.isEmpty()){
                    chunkLeasesMap.remove(lease.getChunkPos());
                }
            }

            //如果是玩家租约，同步更新playerLeaseMap
            if(lease.isPlayer()){
                var playerLeases = playerLeaseMap.get(lease.getHolderId());
                if(playerLeases != null){
                    //检查该区块是否还有其他该玩家的租约
                    var remainingLeases = chunkLeasesMap.get(lease.getChunkPos());
                    boolean hasOtherLease = remainingLeases != null && remainingLeases.stream()
                        .anyMatch(l -> l.isPlayer() && l.getHolderId() == lease.getHolderId());
                    if(!hasOtherLease){
                        playerLeases.remove(lease.getChunkPos());
                        if(playerLeases.isEmpty()){
                            playerLeaseMap.remove(lease.getHolderId());
                        }
                    }
                }
            }

            //发布ServerChunkLeaseExpiredEvent事件
            sweb.publish(new ServerChunkLeaseExpiredEvent(lease.getChunkPos(), lease, this));
            log.debug("移除过期租约 位于:{} 该区块剩余租约数量:{}", lease.getChunkPos(), getRemainingLeaseCount(lease.getChunkPos()));
        }

    }





}
