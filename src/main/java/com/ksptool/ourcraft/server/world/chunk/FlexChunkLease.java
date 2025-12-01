package com.ksptool.ourcraft.server.world.chunk;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.ksptool.ourcraft.sharedcore.utils.position.ChunkPos;
import lombok.Getter;

/**
 * 区块租约，负责管理区块的租约
 * 该类实现安全的相等性判断和哈希码计算 可以用作Map的Key
 */
@Getter
public class FlexChunkLease {

    //租约持有人类型
    public enum HolderType {
        PLAYER,
        SERVER,
    }

    //租约等级
    public enum Level {
        LOW(10),
        MEDIUM(20),
        HIGH(30);
        @Getter
        public final int value;
        Level(int value) {
            this.value = value;
        }
    }

    //持有人ID
    @Getter
    private final long holderId;

    //持有人类型
    private final HolderType holderType;

    //租约等级
    private final Level level;

    //租约剩余时间
    private final AtomicInteger ttl;

    //租约是否永久
    private final AtomicBoolean permanent;

    //区块坐标
    private final ChunkPos chunkPos;

    public FlexChunkLease(ChunkPos chunkPos, HolderType holderType, long holderId, Level level, int ttl) {
        this.chunkPos = chunkPos;
        this.holderType = holderType;
        this.holderId = holderId;
        this.ttl = new AtomicInteger(ttl);
        this.permanent = new AtomicBoolean(true);
        this.level = level;
    }

    /**
     * 创建高级别租约
     * @param chunkPos 区块坐标
     * @param holderType 持有人类型
     * @param holderId 持有人ID
     * @return 租约
     */
    public static FlexChunkLease ofHigh(ChunkPos chunkPos, HolderType holderType, long holderId){
        return new FlexChunkLease(chunkPos, holderType, holderId, Level.HIGH, -1);
    }

    /**
     * 创建中级别租约
     * @param chunkPos 区块坐标
     * @param holderType 持有人类型
     * @param holderId 持有人ID
     * @return 租约
     */
    public static FlexChunkLease ofMedium(ChunkPos chunkPos, HolderType holderType, long holderId){
        return new FlexChunkLease(chunkPos, holderType, holderId, Level.MEDIUM, -1);
    }

    /**
     * 创建低级别租约
     * @param chunkPos 区块坐标
     * @param holderType 持有人类型
     * @param holderId 持有人ID
     * @return 租约
     */
    public static FlexChunkLease ofLow(ChunkPos chunkPos, HolderType holderType, long holderId){
        return new FlexChunkLease(chunkPos, holderType, holderId, Level.LOW, -1);
    }

    /**
     * 续租(如果租约是永久租约则不续租)
     * @param newTTL 新的TTL
     */
    public void renew(int newTTL) {
        if(permanent.get()){
            return;
        }
        ttl.set(newTTL);
    }

    /**
     * 是否是永久租约
     * @return 是否是永久租约
     */
    public boolean isPermanent() {
        return permanent.get();
    }

    /**
     * 设置是否是永久租约
     * @param permanent 是否是永久租约
     */
    public void setPermanent(boolean permanent) {
        this.permanent.set(permanent);
    }

    /**
     * 是否已过期
     * @return 是否已过期
     */
    public boolean isExpired() {
        return ttl.get() <= 0;
    }

    /**
     * 是否是玩家租约
     * @return 是否是玩家租约
     */
    public boolean isPlayer() {
        return holderType == HolderType.PLAYER;
    }

    /**
     * 是否是服务器租约
     * @return 是否是服务器租约
     */
    public boolean isServer() {
        return holderType == HolderType.SERVER;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        FlexChunkLease other = (FlexChunkLease) obj;

        //相同坐标+相同持有人类型 + 相同持有人ID + 相同租约等级 则认为相同
        return chunkPos.equals(other.chunkPos) && holderType == other.holderType && holderId == other.holderId && level == other.level;
    }

    @Override
    public int hashCode() {
        return Objects.hash(chunkPos, holderType, holderId, level);
    }

}
