package com.ksptool.ourcraft.server.world.chunk;

import com.ksptool.ourcraft.server.world.ServerWorld;
import com.ksptool.ourcraft.sharedcore.GlobalPalette;
import com.ksptool.ourcraft.sharedcore.utils.FlexChunkData;
import com.ksptool.ourcraft.sharedcore.utils.position.ChunkPos;
import com.ksptool.ourcraft.sharedcore.world.BlockState;
import com.ksptool.ourcraft.sharedcore.world.SharedChunk;
import lombok.Getter;
import lombok.Setter;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
public class FlexServerChunk implements SharedChunk {

    //全局调色板
    private final GlobalPalette globalPalette;

    public enum Stage {
        /**
         * NEW(新建): 表示区块未初始化,数据未加载 此时loadFuture未完成
         */
        NEW,

        /**
         * PROCESSING_LOAD(加载处理中): 表示区块正在磁盘读取或者地形生成，防止重复提交任务，此时loadFuture未完成
         */
        PROCESSING_LOAD,

        /**
         * READY(就绪): 数据已完整,可进行读写、物理交互 此时loadFuture已完成
         */
        READY,

        /**
         * PROCESSING_UNLOAD(卸载处理中): 表示区块正在被卸载并存盘，防止重复提交任务
         */
        PROCESSING_UNLOAD,

        /**
         * INVALID(无效): 区块已被移除，不应再被使用。(当区块中无玩家，也没有被观看时。服务器会在每个Action中扣减TTL,当TTL小于1时，区块将会被卸载)
         */
        INVALID,
    }

    //物理位置在这个区块内的玩家 UUID
    private final Set<String> playersInside = ConcurrentHashMap.newKeySet();

    //视距包含这个区块的玩家 UUID
    private final Set<String> playersWatching = ConcurrentHashMap.newKeySet();

    //区块状态
    @Setter
    private volatile Stage stage;

    //区块坐标
    @Getter
    private final ChunkPos chunkPos;

    //区块大小X
    private final int sizeX;

    //区块大小Y
    private final int sizeY;

    //区块大小Z
    private final int sizeZ;

    //区块是否脏
    @Setter
    private volatile boolean isDirty = false;

    //区块中的方块数据(Flex)
    private FlexChunkData blockData;

    //所属世界
    private final ServerWorld world;

    //区块生存时间 当区块中没有玩家也没有被观看时每经过一个Action会被扣减1点 当减到0时区块会被卸载
    private final AtomicInteger ttl;

    //区块加载任务
    private final CompletableFuture<FlexServerChunk> loadFuture = new CompletableFuture<>();


    /**
     * 构造函数
     * @param chunkPos 区块坐标
     * @param world 所属世界
     */
    public FlexServerChunk(ChunkPos chunkPos, ServerWorld world){
        this.chunkPos = chunkPos;
        this.world = world;
        var t = world.getTemplate();
        sizeX = t.getChunkSizeX();
        sizeY = t.getChunkSizeY();
        sizeZ = t.getChunkSizeZ();
        blockData = new FlexChunkData(sizeX,sizeY,sizeZ);
        stage = Stage.NEW;
        globalPalette = GlobalPalette.getInstance();
        ttl = new AtomicInteger(t.getChunkMaxTTL());
    }

    public void addPlayerInside(String playerUUID) {
        playersInside.add(playerUUID);
        // 这里可以触发 "玩家进入区块" 事件
    }

    public void removePlayerInside(String playerUUID) {
        playersInside.remove(playerUUID);
        // 这里可以触发 "玩家离开区块" 事件
    }

    public void addWatcher(String playerUUID) {
        playersWatching.add(playerUUID);
        // 如果这是第一个观看者，可能需要初始化某些网络同步状态
    }

    public void removeWatcher(String playerUUID) {
        playersWatching.remove(playerUUID);
        // 如果集合变空，可以将此区块标记为 "待卸载"
    }


    public boolean hasWatchers() {
        return !playersWatching.isEmpty();
    }

    public int getPlayersCount() {
        return playersInside.size();
    }

    @Override
    public boolean isServerSide() {
        return true;
    }

    @Override
    public boolean isClientSide() {
        return false;
    }

    @Override
    public void setBlockState(int x, int y, int z, int stateId) {
        blockData.setBlock(x, y, z, globalPalette.getState(stateId));
        isDirty = true;
    }

    @Override
    public void setBlockState(int x, int y, int z, BlockState state) {
        blockData.setBlock(x, y, z, state);
        isDirty = true;
    }

    @Override
    public BlockState getBlockState(int x, int y, int z) {
        return blockData.getBlock(x, y, z);
    }

    @Override
    public int getBlockStateId(int x, int y, int z) {
        return globalPalette.getStateId(blockData.getBlock(x, y, z));
    }

    @Override
    public int getX() {
        return chunkPos.getX();
    }

    @Override
    public int getZ() {
        return chunkPos.getZ();
    }


    /**
     * 获取原始区块数据
     * @return 原始区块数据
     */
    public FlexChunkData getFlexChunkData(){
        return blockData;
    }

    public void setFlexChunkData(FlexChunkData fcd){

        //为READY状态时无法设置原始区块数据
        if(stage == Stage.READY){
            throw new RuntimeException("区块状态为READY时无法设置原始区块数据");
        }

        //设置原始区块数据
        this.blockData = fcd;
        isDirty = true;
    }

    /**
     * 判断一个坐标是否超出该区块的范围
     * @param x 坐标x
     * @param y 坐标y
     * @param z 坐标z
     * @return 是否超出范围
     */
    public boolean isOutOfRange(int x, int y, int z) {
        return x >= 0 && x < sizeX && y >= 0 && y < sizeY && z >= 0 && z < sizeZ;
    }

    /**
     * 判断一个坐标是否在该区块的范围内
     * @param x 坐标x
     * @param y 坐标y
     * @param z 坐标z
     * @return 是否在范围内
     */
    public boolean isInRange(int x, int y, int z) {
        return !isOutOfRange(x, y, z);
    }


}
