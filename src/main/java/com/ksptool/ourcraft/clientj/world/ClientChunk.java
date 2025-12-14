package com.ksptool.ourcraft.clientj.world;

import com.ksptool.ourcraft.sharedcore.GlobalPalette;
import com.ksptool.ourcraft.sharedcore.utils.FlexChunkData;
import com.ksptool.ourcraft.sharedcore.utils.position.ChunkPos;
import com.ksptool.ourcraft.sharedcore.world.BlockState;
import com.ksptool.ourcraft.sharedcore.world.SharedChunk;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * 客户端区块
 * 用于存储客户端区块数据和状态
 */
@Slf4j
public class ClientChunk implements SharedChunk {

    //区块坐标
    private ChunkPos chunkPos;

    //区块中的方块数据(Flex)
    private FlexChunkData blockData;

    //区块状态
    @Setter
    private volatile Stage stage;


    public ClientChunk(ChunkPos chunkPos, FlexChunkData blockData) {
        this.chunkPos = chunkPos;
        this.blockData = blockData;
        this.stage = Stage.NEED_MESH_UPDATE;
    }

    @Override
    public void setBlockState(int x, int y, int z, int stateId) {
        blockData.setBlock(x, y, z, GlobalPalette.getInstance().getState(stateId));
        this.stage = Stage.NEED_MESH_UPDATE;
    }

    @Override
    public void setBlockState(int x, int y, int z, BlockState state) {
        blockData.setBlock(x, y, z, state);
        this.stage = Stage.NEED_MESH_UPDATE;
    }

    @Override
    public BlockState getBlockState(int x, int y, int z) {
        return blockData.getBlock(x, y, z);
    }

    @Override
    public int getBlockStateId(int x, int y, int z) {
        return GlobalPalette.getInstance().getStateId(blockData.getBlock(x, y, z));
    }

    @Override
    public int getX() {
        return chunkPos.getX();
    }

    @Override
    public int getZ() {
        return chunkPos.getZ();
    }

    @Override
    public int getSizeX() {
        return blockData.getWidth();
    }

    @Override
    public int getSizeY() {
        return blockData.getHeight();
    }

    @Override
    public int getSizeZ() {
        return blockData.getDepth();
    }

    @Override
    public ChunkPos getChunkPos() {
        return chunkPos;
    }

    /**
     * 标记区块为脏
     */
    public void markDirty() {
        this.stage = Stage.NEED_MESH_UPDATE;
    }


    public enum Stage {
        /**
         * NEW(新建): 表示区块未初始化,数据未加载
         */
        NEW,

        /**
         * NEED_MESH_UPDATE(需要Mesh更新): 表示区块需要Mesh更新
         */
        NEED_MESH_UPDATE,

        /**
         * READY(就绪): 数据已完整,可进行读写、物理交互
         */
        READY,

        /**
         * INVALID(无效): 区块已被移除，不应再被使用。
         */
        INVALID,
    }

    
    @Override
    public boolean isServerSide() {
        return false;
    }

    @Override
    public boolean isClientSide() {
        return true;
    }
}
