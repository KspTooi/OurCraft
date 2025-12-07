package com.ksptool.ourcraft.server.world.chunk;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import com.ksptool.ourcraft.server.entity.ServerPlayer;
import com.ksptool.ourcraft.server.event.ServerChunkLeaseExpiredEvent;
import com.ksptool.ourcraft.server.event.ServerChunkLeaseIssuedEvent;
import com.ksptool.ourcraft.server.world.ServerWorld;
import com.ksptool.ourcraft.server.world.ServerWorldEventService;
import com.ksptool.ourcraft.server.world.ServerWorldTimeService;
import com.ksptool.ourcraft.server.world.SimpleEntityService;
import com.ksptool.ourcraft.server.world.chunk.FlexChunkLease.HolderType;
import com.ksptool.ourcraft.server.world.chunk.FlexChunkLease.Level;
import com.ksptool.ourcraft.sharedcore.utils.position.ChunkPos;
import com.ksptool.ourcraft.sharedcore.utils.viewport.ChunkViewPort;
import com.ksptool.ourcraft.sharedcore.world.SharedWorld;
import com.ksptool.ourcraft.sharedcore.world.WorldService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * 区块租约服务，负责管理区块的租约
 */
@Slf4j
public class FlexChunkLeaseService extends WorldService{

    //发生变化的区块集合最大大小
    private static final int MAX_CHANGES_QUEUE_SIZE = 50000;

    //区块坐标->区块租约集合
    private final Map<ChunkPos, Set<FlexChunkLease>> chunkLeasesMap = new ConcurrentHashMap<>();

    //Player SessionID->该玩家持有的所有区块坐标集
    private final Map<Long, Set<ChunkPos>> playerLeaseMap = new ConcurrentHashMap<>();

    //用于存储在action中发生变化的区块(原理: 这个集合会记录在每一次Action更新中发生变化的租约) 无论是主线程还是网络线程，统一写入这里，安全且无竞态
    private final Set<ChunkPos> changes = ConcurrentHashMap.newKeySet();

    //有序队列(用于存储过期租约 过期时间越早的租约越靠前)
    private final Queue<FlexChunkLease> expiredLeases = new PriorityBlockingQueue<>(64,Comparator.comparingLong(FlexChunkLease::getExpireAt));

    @Getter
    private final ServerWorld world;

    private final SimpleEntityService ses;

    private final ServerWorldTimeService swts;

    //租约过期时间
    private final int maxPlayerChunkLeaseAction;

    public FlexChunkLeaseService(ServerWorld world) {
        this.world = world;
        this.ses = world.getSes();
        this.swts = world.getSwts();
        this.maxPlayerChunkLeaseAction = world.getTemplate().getMaxPlayerChunkLeaseAction();
    }

    @Override
    public void initOrReload() {
        super.initOrReload();
    }

    /**
     * 签发租约(此函数签发Server级别永久租约)
     * @param chunkPos 区块坐标
     */
    public void issuePermanentServerLease(ChunkPos chunkPos) {

        chunkLeasesMap.compute(chunkPos, (cp, set) -> {

            //无SET则创建新的SET
            if(set == null){
                set = ConcurrentHashMap.newKeySet();
            }

            //从SET中获取最高等级的服务器租约
            var existsServerLease = getLeaseInternal(set, FlexChunkLease.HolderType.SERVER, FlexChunkLease.Level.HIGH);

            //如果同Chunk存在一个非永久的服务器租约则升级它为永久租约
            if(existsServerLease != null){

                //如果已经是永久租约则直接返回
                if(existsServerLease.isPermanent()){
                    return set;
                }

                existsServerLease.upgradeToPermanent();
                return set;
            }

            //创建新的租约
            var newLease = FlexChunkLease.ofHigh(chunkPos, FlexChunkLease.HolderType.SERVER, -1);
            set.add(newLease);

            //标记区块发生变化
            markChangedInternal(chunkPos);
            return set;
        });

    }

    /**
     * 签发永久租约(此函数签发Player级别永久租约)
     * @param chunkPos 区块坐标
     * @param playerSessionId Player SessionID
     */
    public void issuePermanentLease(ChunkPos chunkPos, long playerSessionId) {
        
        //查询玩家是否已持有该区块POS
        playerLeaseMap.compute(playerSessionId, (cp, set) -> {

            if(set == null){
                set = ConcurrentHashMap.newKeySet();
            }

            //玩家在该区块POS已持有若干租约
            if(set.contains(chunkPos)){

                //查询一个最高等级的租约
                var lease = getLeaseInternal(chunkLeasesMap.get(chunkPos), playerSessionId, FlexChunkLease.Level.HIGH);

                //如果该租约已经是永久租约则直接返回
                if(lease != null){
                    if(lease.isPermanent()){
                        return set;
                    }
                    //升级为永久租约
                    lease.upgradeToPermanent();
                    return set;
                }

                return set;
            }

            //为玩家签发新租约
            var newLease = FlexChunkLease.ofHigh(chunkPos, FlexChunkLease.HolderType.PLAYER, playerSessionId);
            set.add(chunkPos); //标记玩家持有该区块POS

            //标记区块发生变化
            markChangedInternal(chunkPos);

            //添加到区块租约集合
            chunkLeasesMap.compute(chunkPos, (ccp, cSet) -> {
                if(cSet == null){
                    cSet = ConcurrentHashMap.newKeySet();
                }
                cSet.add(newLease);
                return cSet;
            });

            return set;
        });

    }


    /**
     * 降级永久租约(此函数降级Player级别永久租约使其降级为一个有限期租约，这不会删除任何租约 只是将它们降级(删除逻辑应统一到Action))
     * @param chunkPos 区块坐标
     * @param playerSessionId 玩家SessionID
     */
    public void downgradePermanentLease(ChunkPos chunkPos, long playerSessionId) {

        //查询玩家是否已持有该区块POS
        playerLeaseMap.computeIfPresent(playerSessionId, (cp, set) -> {

            //不持有该区块POS则直接返回
            if(!set.contains(chunkPos)){
                return set;
            }

            //查询一个最高等级的租约
            var lease = getLeaseInternal(chunkLeasesMap.get(chunkPos), playerSessionId, FlexChunkLease.Level.HIGH);
            if(lease == null){
                return set;
            }

            //降级为有限期租约
            lease.downgradeToFinite(getNextExpireTime());

            //添加到过期租约队列(过期时间越早的租约越靠前)
            expiredLeases.add(lease);
            return set;
        });

    }


    /**
     * 获取租约等级
     * @param chunkPos 区块坐标
     * @return 租约等级 如果这个ChunkPos不存在任何租约则返回null，否则返回最高等级的租约等级
     */
    public FlexChunkLease.Level getLeaseLevel(ChunkPos chunkPos){

        var set = chunkLeasesMap.get(chunkPos);
        if(set == null){
            return null;
        }

        var level = -1;

        for(var lease : set){
            if(lease.getLevel().getValue() > level){
                level = lease.getLevel().getValue();
            }
        }

        if(level == -1){
            return null;
        }

        return Level.of(level);
    }

    /**
     * 获取租约
     * @param chunkPos 区块坐标
     * @return 租约 如果这个ChunkPos不存在任何租约则返回null，返回最高等级的租约
     */
    public FlexChunkLease getLease(ChunkPos chunkPos){

        var set = chunkLeasesMap.get(chunkPos);
        if(set == null){
            return null;
        }

        var level = -1;
        FlexChunkLease result = null;

        for (FlexChunkLease lease : set) {
            if (lease.getLevel().getValue() > level) {
                level = lease.getLevel().getValue();
                result = lease;
            }
        }

        if(level == -1){
            return null;
        }

        return result;
    }

    /**
     * 获取租约
     * @param cp 区块坐标
     * @param ht 持有人类型
     * @param l 租约等级
     * @return 租约 不存在则返回null
     */
    public FlexChunkLease getLease(ChunkPos cp, FlexChunkLease.HolderType ht, FlexChunkLease.Level l){

        var set = chunkLeasesMap.get(cp);

        if(set == null){
            return null;
        }

        for(var it = set.iterator(); it.hasNext();){
            var lease = it.next();
            if(lease.getHolderType() == ht && lease.getLevel() == l){
                return lease;
            }
        }

        return null;
    }



    /**
     * 统计该位置有效的剩余租约数量
     * @param chunkPos 区块坐标
     * @return 剩余租约数量
     */
    public long getRemainingLeaseCount(ChunkPos chunkPos){
        var set = chunkLeasesMap.get(chunkPos);
        if(set == null){
            return 0;
        }
        return set.size();
    }


    /**
     * 执行更新(这不是一个线程安全的方法 不可在多线程中调用)
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
                    issuePermanentLease(vPos, p.getSession().getId());
                }
                p.markLeaseInited();
                continue;
            }

            //已经完成租约初始化,则需要更新租约,先判断Player有没有离开当前区块
            if(!p.getCurrentChunkPos().equals(p.getPreviousChunkPos())){

                var newViewport = ChunkViewPort.of(p.getCurrentChunkPos(), p.getViewDistance());
                var newViewportChunks = newViewport.getChunkPosSet();

                //降级不在新视口内的区块租约
                var playerLeases = playerLeaseMap.get(p.getSession().getId());

                if(playerLeases != null){
                    for(var leaseChunkPos : playerLeases){
                        if(!newViewport.contains(leaseChunkPos)){
                            downgradePermanentLease(leaseChunkPos, p.getSession().getId());
                        }
                    }
                }

                //为新视口内的区块签发租约（如果不存在）
                for(var chunkPos : newViewportChunks){
                    issuePermanentLease(chunkPos, p.getSession().getId());
                }
            }

        }

        //过期队列处理
        while(!expiredLeases.isEmpty()){

            var firstExpiredLease = expiredLeases.peek();

            if(firstExpiredLease == null){
                break;
            }
            
            //当过期租约队列中有永久租约时，则直接移除它们并继续处理下一个过期租约
            if(firstExpiredLease.isPermanent()){
                expiredLeases.poll();
                continue;
            }

            //查看第一个过期租约是否已过期
            if(firstExpiredLease.isExpired(swts.getTotalActions())){

                //处理过期租约移除
                removeLeaseInternal(expiredLeases.poll());

                //继续处理下一个过期租约
                continue;
            }

            //如果第一个过期租约未过期，则跳出循环
            if(!firstExpiredLease.isExpired(swts.getTotalActions())){
                break;
            }

            break;
        }

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

    /**
     * 获取租约
     * @param leases 租约集合
     * @param holderId 持有人ID
     * @param l 租约等级
     * @return 租约 不存在则返回null
     */
    private FlexChunkLease getLeaseInternal(Set<FlexChunkLease> leases, long holderId, FlexChunkLease.Level l){
        for (FlexChunkLease lease : leases) {
            if (lease.getHolderType() == HolderType.PLAYER && lease.getHolderId() == holderId && lease.getLevel() == l) {
                return lease;
            }
        }
        return null;
    }

    /**
     * 获取租约
     * @param leases 租约集合
     * @param ht 持有人类型
     * @param l 租约等级
     * @return 租约 不存在则返回null
     */
    private FlexChunkLease getLeaseInternal(Set<FlexChunkLease> leases, FlexChunkLease.HolderType ht, FlexChunkLease.Level l){

        for (FlexChunkLease lease : leases) {
            if (lease.getHolderType() == ht && lease.getLevel() == l) {
                return lease;
            }
        }

        return null;
    }

    /**
     * 获取租约下次过期时间
     * @return 租约下次过期时间(以Action为单位)
     */
    private long getNextExpireTime(){
        return swts.getTotalActions() + maxPlayerChunkLeaseAction;
    }

    /**
     * 完全移除租约(无论是否永久租约)
     * @param lease 租约
     */
    private void removeLeaseInternal(FlexChunkLease lease){

        if(lease == null){
            return;
        }

        var chunk = lease.getChunkPos();

        //先清除区块租约集合
        chunkLeasesMap.computeIfPresent(chunk, (cp, set) -> {

            set.remove(lease);

            //如果租约是Player租约 则清除Player租约集合
            if(lease.isPlayerHolder()){

                playerLeaseMap.computeIfPresent(lease.getHolderId(), (_, pSet) -> {

                    pSet.remove(lease.getChunkPos());

                    //如果Player租约集合中的SET已经没有租约了 则清除KEY
                    if(pSet.isEmpty()){
                        return null;
                    }

                    return pSet;
                });

            }

            //如果区块租约集合中的SET已经没有租约了 则清除KEY
            if(set.isEmpty()){
                return null;
            }

            return set;
        });

    }

    /**
     * 标记发生变化的区块
     * @param chunkPos 区块坐标
     */
    private void markChangedInternal(ChunkPos chunkPos) {
        //防止被恶意攻击撑爆内存
        if (changes.size() >= MAX_CHANGES_QUEUE_SIZE) {
            //只有在极度异常（如受到攻击或主线程死锁）时才会触发
            //打印日志并拒绝写入
            log.error("发生变化的区块集合产生溢出!！ 当前容量: {} 最大容量: {}", changes.size(), MAX_CHANGES_QUEUE_SIZE);
            return;
        }
        changes.add(chunkPos);
    }


}
