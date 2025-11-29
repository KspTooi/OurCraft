package com.ksptool.ourcraft.sharedcore.utils.position;

import lombok.Getter;

/**
 * SCA坐标系
 * 
 * 坐标系 SCA坐标系: 表示这个区块的数据应该存在硬盘上的哪个编号的.SCA文件
 */
@Getter
public class ScaPos {

    private final int x;
    private final int z;

    /**
     * 构造函数
     * @param x SCA X坐标
     * @param z SCA Z坐标
     */
    private ScaPos(int x, int z) {
        this.x = x;
        this.z = z;
    }

    /**
     * 创建一个SCA位置
     * @param x SCAX坐标
     * @param z SCAZ坐标
     * @return SCA位置
     */
    public static ScaPos of(int x, int z) {
        return new ScaPos(x, z);
    }
    
    @Override
    public String toString() {
        return "SCA坐标:[" + x + "," + z + "]";
    }
}
