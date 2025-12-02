package com.ksptool.ourcraft.server.world.chunk;

import java.util.*;
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

    //发生变化的区块集合最大大小
    private static final int MAX_CHANGES_QUEUE_SIZE = 50000;

    //区块坐标->区块租约集合(与playerLeaseMap强一致性)
    private final Map<ChunkPos, Set<FlexChunkLease>> chunkLeasesMap = new ConcurrentHashMap<>();

    //Player SessionID->该玩家持有的所有区块坐标集合(用于快速查找 与chunkLeasesMap强一致性)
    private final Map<Long, Set<ChunkPos>> playerLeaseMap = new ConcurrentHashMap<>();

    //用于存储在action中发生变化的区块(原理: 这个集合会记录在每一次Action更新中发生变化的租约) 无论是主线程还是网络线程，统一写入这里，安全且无竞态
    private final Set<ChunkPos> changes = ConcurrentHashMap.newKeySet();

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
     */
    public void issuePermanentServerLease(ChunkPos chunkPos) {

        if(chunkPos == null){
            throw new IllegalArgumentException("无法签发租约: 区块坐标不能为空");
        }

        chunkLeasesMap.compute(chunkPos, (pos, leases) -> {

            if (leases == null){
                leases = ConcurrentHashMap.newKeySet();
            }

            var lease = FlexChunkLease.ofHigh(pos, FlexChunkLease.HolderType.SERVER, -1);
            lease.getPermanent().set(true);

            if(leases.add(lease)){
                markChanged(chunkPos);
                //发布ServerChunkLeaseIssuedEvent事件
                sweb.publish(new ServerChunkLeaseIssuedEvent(chunkPos, lease, this));
                log.debug("签发服务器级别永久租约 区块:{} 等级:{}", chunkPos, lease.getLevel());
            }
            return leases;
        });

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

        chunkLeasesMap.compute(chunkPos, (pos, leases) -> {

            //租约Set为空 创建新Set
            if (leases == null){
                leases = ConcurrentHashMap.newKeySet();
            }

            //检查Set中是否已存在该玩家最高等级的租约 如果有 直接提升它为永久租约
            for(var existingLease : leases){

                if(existingLease.isPlayer() && existingLease.getHolderId() == playerSessionId){
                    if(existingLease.getLevel().equals(FlexChunkLease.Level.HIGH)){
                        if(!existingLease.isPermanent()){
                            existingLease.setPermanent(true);
                            existingLease.renew(world.getTemplate().getMaxPlayerChunkLeaseAction()); //恢复TTL到最大值
                            markChanged(chunkPos);
                            sweb.publish(new ServerChunkLeaseIssuedEvent(chunkPos, existingLease, this));
                            log.debug("提升玩家级别租约为永久租约 区块:{} 玩家SessionID:{}", chunkPos, playerSessionId);
                        }
                        return leases;
                    }
                }

            }

            //如果Player在该区块没有最高等级的临时租约 则创建新的永久租约
            var newLease = FlexChunkLease.ofHigh(chunkPos, FlexChunkLease.HolderType.PLAYER, playerSessionId);
            newLease.setPermanent(true);
            newLease.renew(world.getTemplate().getMaxPlayerChunkLeaseAction()); //恢复TTL到最大值

            if(leases.add(newLease)){
                markChanged(chunkPos);
                //将新的租约添加到玩家租约池中并发布ServerChunkLeaseIssuedEvent事件
                playerLeaseMap.computeIfAbsent(playerSessionId, k -> ConcurrentHashMap.newKeySet()).add(chunkPos);
                sweb.publish(new ServerChunkLeaseIssuedEvent(chunkPos, newLease, this));
                log.debug("签发玩家级别永久租约 区块:{} 玩家SessionID:{} 等级:{}", chunkPos, playerSessionId, newLease.getLevel());
            }

            return leases;
        });

    }


    /**
     * 吊销永久租约(此函数吊销Player级别永久租约使其降级为一个有限期租约，这不会删除任何租约 只是将它们降级(删除逻辑应统一到Action))
     * @param chunkPos 区块坐标
     * @param playerSessionId 玩家SessionID
     */
    public void revokePermanentLease(ChunkPos chunkPos, long playerSessionId) {

        if(chunkPos == null || playerSessionId == -1){
            throw new IllegalArgumentException("无法吊销租约: 区块坐标或SessionID不能为空");
        }

        chunkLeasesMap.compute(chunkPos, (pos, chunkLeases) -> {

            if(chunkLeases == null){
                return null;
            }

            //搜集需要吊销的租约
            List<FlexChunkLease> revokedLeases = new ArrayList<>();
            for(var lease : chunkLeases){
                if(!lease.isPlayer() || lease.getHolderId() != playerSessionId){
                    continue;
                }
                if(!lease.isPermanent()){
                    continue;
                }
                revokedLeases.add(lease);
                lease.setPermanent(false);
                lease.renew(world.getTemplate().getMaxPlayerChunkLeaseAction());
            }

            //如果需要吊销的租约集合为空，直接返回该区块剩余的其他租约
            if(revokedLeases.isEmpty()){
                return chunkLeases;
            }

            //标记该区块发生变化
            markChanged(chunkPos);
            log.debug("吊销玩家级别永久租约 区块:{} 玩家SessionID:{} 数量:{}", chunkPos, playerSessionId, revokedLeases.size());
            return chunkLeases;
        });

    }


    /**
     * 获取租约等级
     * @param chunkPos 区块坐标
     * @return 租约等级 如果这个ChunkPos不存在任何租约则返回null，否则返回最高等级的租约等级
     */
    public FlexChunkLease.Level getLeaseLevel(ChunkPos chunkPos){
        var leases = chunkLeasesMap.get(chunkPos);
        if(leases == null || leases.isEmpty()){
            return null;
        }
        
        return leases.stream()
            .filter(lease -> !lease.isExpired())
            .map(FlexChunkLease::getLevel)
            .max(Comparator.comparingInt(FlexChunkLease.Level::getValue))
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
        
        //玩家租约管理
        for(var entity : entities){

            if(!(entity instanceof ServerPlayer p)){
                continue;
            }

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
                    issuePermanentLease(chunkPos, p.getSessionId());
                }
            }

        }


        //扣减租约TTL并移除过期租约
        var expiredLeases = new ArrayList<FlexChunkLease>();

        //遍历所有租约，扣减TTL并收集过期租约
        for(var leases : chunkLeasesMap.values()){
            for(var lease : leases){
                lease.action(delta, world);
                if(lease.isExpired()){
                    expiredLeases.add(lease);
                }
            }
        }
        
        //遍历清理过期租约
        for(var expireLease : expiredLeases){

            ChunkPos chunkPos = expireLease.getChunkPos();

            chunkLeasesMap.computeIfPresent(chunkPos, (cp, leasesSet) -> {

                if (leasesSet.remove(expireLease)) {

                    //如果是玩家租约 需要处理玩家租约索引
                    if(expireLease.isPlayer()){

                        playerLeaseMap.computeIfPresent(expireLease.getHolderId(),(k,v)->{

                            boolean hasOther = leasesSet.stream()
                                    .anyMatch(l -> l.isPlayer() && l.getHolderId() == expireLease.getHolderId());

                            if (!hasOther) {
                                v.remove(chunkPos); // 安全移除
                            }

                            return v.isEmpty() ? null : v;
                        });

                    }

                    markChanged(expireLease.getChunkPos());
                    sweb.publish(new ServerChunkLeaseExpiredEvent(expireLease.getChunkPos(), expireLease, this));
                    log.debug("移除过期租约 位于:{} 该区块剩余租约数量:{}", expireLease.getChunkPos(), getRemainingLeaseCount(expireLease.getChunkPos()));
                }

                return leasesSet.isEmpty() ? null : leasesSet;
            });

        }

    }

    private void markChanged(ChunkPos chunkPos) {
        //防止被恶意攻击撑爆内存
        if (changes.size() >= MAX_CHANGES_QUEUE_SIZE) {
            //只有在极度异常（如受到攻击或主线程死锁）时才会触发
            //打印日志并拒绝写入
            log.error("发生变化的区块集合产生溢出!！ 当前容量: {} 最大容量: {}", changes.size(), MAX_CHANGES_QUEUE_SIZE);
            return;
        }
        changes.add(chunkPos);
    }


    /**
     * 获取并清空发生变化的区块集合
     * @return 发生变化的区块集合
     */
    public Set<ChunkPos> pollChanges() {
        if (changes.isEmpty()) return Collections.emptySet();

        Set<ChunkPos> snapshot = new HashSet<>();
        var it = changes.iterator();
        while(it.hasNext()){
            snapshot.add(it.next());
            it.remove(); // 使用迭代器移除
        }
        return snapshot;
    }

}
