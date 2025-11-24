package com.ksptool.ourcraft.server.world.chunk;


import com.ksptool.ourcraft.server.world.ServerWorld;
import com.ksptool.ourcraft.sharedcore.GlobalPalette;
import com.ksptool.ourcraft.sharedcore.Registry;
import com.ksptool.ourcraft.sharedcore.blocks.inner.SharedBlock;
import com.ksptool.ourcraft.sharedcore.enums.BlockEnums;
import com.ksptool.ourcraft.sharedcore.utils.ChunkBlockData;
import com.ksptool.ourcraft.sharedcore.world.BlockState;
import lombok.Getter;
import lombok.Setter;

@Getter
public class ServerSuperChunk {

    public enum ChunkState {
        NEW,             //未初始化
        AWAITING_GEN,    //等待区块生成
        AWAITING_LOAD,   //等待区块加载(从SCA)
        READY,           //正常
        AWAITING_UNLOAD, //等待区块卸载并保存到SCA
    }

    //区块状态
    @Setter
    private ChunkState state;

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
    private boolean isDirty = false;

    //区块数据
    private ChunkBlockData blockData;

    //所属世界
    private ServerWorld world;

    /**
     * 构造函数
     * @param x 区块坐标X
     * @param z 区块坐标Z
     * @param world 所属世界
     */
    public ServerSuperChunk(int x, int z, ServerWorld world){
        this.x = x;
        this.z = z;
        this.world = world;
        //获取区块大小
        var t = world.getTemplate();
        sizeX = t.getChunkSizeX();
        sizeY = t.getChunkSizeY();
        sizeZ = t.getChunkSizeZ();
        blockData = new ChunkBlockData(sizeX,sizeY,sizeZ);
        state = ChunkState.NEW;
    }

    /**
     * 设置区块方块
     * @param x 方块坐标X
     * @param y 方块坐标Y
     * @param z 方块坐标Z
     * @param block 方块状态
     */
    public void setBlock(int x, int y, int z,BlockState block){
        if(isInRange(x,y,z)){
            blockData.setBlock(x,y,z,block);
            this.isDirty = true;
        }
    }

    /**
     * 获取区块方块
     * @param x 方块坐标X
     * @param y 方块坐标Y
     * @param z 方块坐标Z
     * @return 方块状态
     */
    public BlockState getBlock(int x, int y, int z) {

        if(isOutOfRange(x, y, z)){
            return Registry.getInstance().getBlock(BlockEnums.AIR.getStdRegName()).getDefaultState();
        }

        return blockData.getBlock(x,y,z);
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
