package com.ksptool.ourcraft.server.world.chunk;

import com.ksptool.ourcraft.server.world.ServerWorld;
import com.ksptool.ourcraft.sharedcore.GlobalPalette;
import com.ksptool.ourcraft.sharedcore.utils.FlexChunkData;
import com.ksptool.ourcraft.sharedcore.world.BlockState;
import com.ksptool.ourcraft.sharedcore.world.SharedChunk;
import lombok.Getter;
import lombok.Setter;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
public class FlexServerChunk implements SharedChunk {

    //全局调色板
    private final GlobalPalette globalPalette;

    public enum ChunkState {
        NEW,              //未初始化
        COMMITED_LOAD,    //已提交区块生成或加载任务
        FINISH_LOAD,      //区块生成或加载完成
        READY,            //区块已准备好
        AWAITING_UNLOAD,  //等待区块卸载并保存到SCA
        UNLOADED,         //区块已卸载
    }

    //物理位置在这个区块内的玩家 UUID
    private final Set<String> playersInside = ConcurrentHashMap.newKeySet();

    //视距包含这个区块的玩家 UUID
    private final Set<String> playersWatching = ConcurrentHashMap.newKeySet();

    //区块状态
    @Setter
    private volatile ChunkState state;

    //区块坐标X
    private final int x;

    //区块坐标Z
    private final int z;

    //区块大小X
    private final int sizeX;

    //区块大小Y
    private final int sizeY;

    //区块大小Z
    private final int sizeZ;

    //区块是否脏
    @Setter
    private boolean isDirty = false;

    //区块数据
    private FlexChunkData blockData;

    //所属世界
    private final ServerWorld world;

    //区块生存时间 当区块中没有玩家也没有被观看时每Tick减一 当减到0时区块会被卸载
    private final AtomicInteger ttl;


    /**
     * 构造函数
     * @param x 区块坐标X
     * @param z 区块坐标Z
     * @param world 所属世界
     */
    public FlexServerChunk(int x, int z, ServerWorld world){
        this.x = x;
        this.z = z;
        this.world = world;
        var t = world.getTemplate();
        sizeX = t.getChunkSizeX();
        sizeY = t.getChunkSizeY();
        sizeZ = t.getChunkSizeZ();
        blockData = new FlexChunkData(sizeX,sizeY,sizeZ);
        state = ChunkState.NEW;
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

    /**
     * 获取原始区块数据
     * @return 原始区块数据
     */
    public FlexChunkData getRawBlockData(){
        return blockData;
    }

    public void setRawBlockData(FlexChunkData blockData){

        //为READY状态时无法设置原始区块数据
        if(state == ChunkState.READY){
            throw new RuntimeException("区块状态为READY时无法设置原始区块数据");
        }

        //设置原始区块数据
        this.blockData = blockData;
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
