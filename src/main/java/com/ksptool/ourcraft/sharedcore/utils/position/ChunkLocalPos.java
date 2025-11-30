package com.ksptool.ourcraft.sharedcore.utils.position;

import lombok.Getter;

/**
 * 区块内局部坐标系
 * 
 * 坐标系 区块内局部坐标系: 用于表示"这个方块"在这个区块内的相对坐标
 */
@Getter
public class ChunkLocalPos {

    private final int x;
    private final int y;
    private final int z;

    /**
     * 构造函数
     * @param x 局部X坐标
     * @param y 局部Y坐标
     * @param z 局部Z坐标
     */
    private ChunkLocalPos(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * 创建一个区块内局部位置
     * @param x 局部X坐标
     * @param y 局部Y坐标
     * @param z 局部Z坐标
     * @return 区块内局部位置
     */
    public static ChunkLocalPos of(int x, int y, int z) {
        return new ChunkLocalPos(x, y, z);
    }

    @Override
    public String toString() {
        return "本地坐标:[" + x + "," + y + "," + z + "]";
    }

}
