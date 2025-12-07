package com.ksptool.ourcraft.server.world.chunk;

import java.util.Objects;
import com.ksptool.ourcraft.sharedcore.utils.position.ChunkPos;
import lombok.Getter;

/**
 * 区块租约，负责管理区块的租约
 * 该类实现安全的相等性判断和哈希码计算 可以用作Map的Key
 */
@Getter
public class FlexChunkLease{

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
        public static Level of(int value){
            for(var level : Level.values()){
                if(level.value == value){
                    return level;
                }
            }
            return HIGH;
        }
    }

    //持有人ID 如果持有人类型为Server则必须为-1
    @Getter
    private final long holderId;

    //持有人类型
    private final HolderType holderType;

    //租约等级
    private final Level level;

    //租约过期时间(大于指定Action后过期) 如果为-1则表示永久租约
    private volatile long expireAt;

    //区块坐标
    private final ChunkPos chunkPos;

    public FlexChunkLease(ChunkPos chunkPos, HolderType holderType, long holderId, Level level, long expireAt) {

        if(chunkPos == null || holderType == null || level == null){
            throw new IllegalArgumentException("ChunkPos、HolderType、Level不能为空或null");
        }
        if(holderType == HolderType.SERVER && holderId != -1){
            throw new IllegalArgumentException("Server租约的持有人ID必须为-1");
        }

        this.chunkPos = chunkPos;
        this.holderType = holderType;
        this.holderId = holderId;
        this.expireAt = expireAt;
        this.level = level;

        //如果过期时间小于0 则设置为永久租约
        if(this.expireAt < 0){
            this.expireAt = -1;
        }

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
     * 设置租约过期时间(该函数线程安全)
     * @param expireAt 过期时间
     */
    public synchronized void setExpireAt(long expireAt) {
        if(expireAt < 0){
            throw new IllegalArgumentException("过期时间不能小于0");
        }
        this.expireAt = expireAt;
    }


    /**
     * 升级为永久租约(该函数线程安全)
     */
    public synchronized void upgradeToPermanent() {
        this.expireAt = -1;
    }

    /**
     * 降级为有限期租约(该函数线程安全)
     * @param expireAt 过期时间
     */
    public synchronized void downgradeToFinite(long expireAt) {
        this.expireAt = expireAt;
    }

    /**
     * 是否已过期
     * @return 是否已过期
     */
    public boolean isExpired(long currentAction) {

        //永久租约不会过期
        if(expireAt == -1){
            return false;
        }

        return currentAction > expireAt;
    }

    /**
     * 是否是永久租约
     * @return 是否是永久租约
     */
    public boolean isPermanent() {
        return expireAt == -1;
    }

    /**
     * 是否是Player租约
     * @return 是否是Player租约
     */
    public boolean isPlayerHolder() {
        return holderType == HolderType.PLAYER;
    }

    /**
     * 是否是Server租约
     * @return 是否是Server租约
     */
    public boolean isServerHolder() {
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

        //相同坐标 + 相同持有人类型 + 相同持有人ID + 相同租约等级 则认为相同
        return chunkPos.equals(other.chunkPos) && holderType == other.holderType && holderId == other.holderId && level == other.level;
    }

    @Override
    public int hashCode() {
        return Objects.hash(chunkPos, holderType, holderId, level);
    }

}
