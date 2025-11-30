package com.ksptool.ourcraft.sharedcore.world;

import com.ksptool.ourcraft.sharedcore.utils.position.ChunkPos;

public interface SharedChunk {

    /**
     * 是否是服务端
     * @return 是否是服务端
     */
    boolean isServerSide();

    /**
     * 是否是客户端
     * @return 是否是客户端
     */
    boolean isClientSide();


    /**
     * 设置方块状态
     * @param x 方块坐标X
     * @param y 方块坐标Y
     * @param z 方块坐标Z
     * @param stateId 方块状态ID
     */
    void setBlockState(int x, int y, int z, int stateId);

    /**
     * 设置方块状态
     * @param x 方块坐标X
     * @param y 方块坐标Y
     * @param z 方块坐标Z
     * @param state 方块状态
     */
    void setBlockState(int x, int y, int z, BlockState state);


    /**
     * 获取方块状态
     * @param x 方块坐标X
     * @param y 方块坐标Y
     * @param z 方块坐标Z
     * @return 方块状态
     */
    BlockState getBlockState(int x, int y, int z);

    /**
     * 获取方块状态ID(全局调色板ID)
     * @param x 方块坐标X
     * @param y 方块坐标Y
     * @param z 方块坐标Z
     * @return 方块状态
     */
    int getBlockStateId(int x, int y, int z);


    /**
     * 获取区块X坐标
     * @return 区块X坐标
     */
    int getX();

    /**
     * 获取区块Z坐标
     * @return 区块Z坐标
     */
    int getZ();
    
    /**
     * 获取区块大小X
     * @return 区块大小X
     */ 
    int getSizeX();

    /**
     * 获取区块大小Y
     * @return 区块大小Y
     */
    int getSizeY();
    
    /**
     * 获取区块大小Z
     * @return 区块大小Z
     */
    int getSizeZ();

    
    ChunkPos getChunkPos();

}
