package com.ksptool.ourcraft.sharedcore.utils.position;

import lombok.Getter;

/**
 * 区块位置类
 * 
 * 坐标系 区块坐标系: 用于表示"这个方块"属于第几个区块
 */
@Getter
public class ChunkPos {

    private final int x;
    private final int y;
    private final int z;

    /**
     * 构造函数
     * @param x 区块X坐标
     * @param y 区块Y坐标
     * @param z 区块Z坐标
     */
    private ChunkPos(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * 创建一个区块位置
     * @param x 区块X坐标
     * @param y 区块Y坐标
     * @param z 区块Z坐标
     * @return 区块位置
     */
    public static ChunkPos of(int x, int z) {
        return new ChunkPos(x, 0, z);
    }

    public static ChunkPos of(int x, int y, int z) {
        return new ChunkPos(x, y, z);
    }

    /**
     * 转换区块坐标为SCA封装坐标（指定SCA封装大小）
     * 此方法允许指定自定义的SCA封装大小，适用于使用非标准SCA封装大小的存档系统。
     * @param scaPkgSize SCA封装大小（通常为40 即一个SCA文件封装40X40个区块）
     * @return SCA包坐标对象
     * @throws IllegalArgumentException 如果SCA包大小小于等于0
     */
    public ScaPos toScaPos(int scaPkgSize) {

        // 检查SCA封装大小是否大于0
        if (scaPkgSize <= 0) {
            throw new IllegalArgumentException("SCA封装大小必须大于0: scaPkgSize=" + scaPkgSize);
        }

        /*
        计算与转换说明：
        1. SCA坐标计算原理：将区块坐标除以SCA封装大小并向下取整得到区域坐标
           公式：regionX = chunkX / scaPkgSize（正数）或 (chunkX + 1) / scaPkgSize - 1（负数）
           例如：区块坐标 chunkX=80, scaPkgSize=40
           计算：80 / 40 = 2，得到 regionX = 2
           表示该区块位于第2个SCA文件（SCA文件范围：区块80-119）

        2. 负数坐标处理：负数区块坐标需要特殊处理以确保正确分组
           例如：区块坐标 chunkX=-5, scaPkgSize=40
           计算：(-5 + 1) / 40 - 1 = -4 / 40 - 1 = -1
           如果使用简单除法 -5 / 40 = 0（错误），正确计算得到 -1（正确）

        3. SCA坐标仅使用X和Z：SCA坐标表示区域文件位置，不包含Y坐标信息
           因为SCA文件是按区域组织的，Y坐标信息保留在区块数据中 */
        int regionX = x >= 0 ? x / scaPkgSize : (x + 1) / scaPkgSize - 1;
        int regionZ = z >= 0 ? z / scaPkgSize : (z + 1) / scaPkgSize - 1;
        
        return ScaPos.of(regionX, regionZ);
    }

    /**
     * 转换区块坐标为SCA文件内局部区块坐标（指定SCA封装大小）
     * 此方法计算区块在SCA文件内部的相对位置，范围通常为0-39。
     * @param scaPkgSize SCA封装大小（通常为40 即一个SCA文件封装40X40个区块）
     * @return SCA文件内局部区块坐标对象
     * @throws IllegalArgumentException 如果SCA包大小小于等于0
     */
    public ScaLocalPos toScaLocalPos(int scaPkgSize) {
        if (scaPkgSize <= 0) {
            throw new IllegalArgumentException("SCA封装大小必须大于0: scaPkgSize=" + scaPkgSize);
        }

        /*
        计算与转换说明：
        1. SCA内坐标计算原理：先计算SCA区域坐标，然后用区块坐标减去区域原点坐标
           公式：localX = chunkX - regionX * scaPkgSize
           例如：区块坐标 chunkX=85, scaPkgSize=40
           计算：regionX = 85 / 40 = 2
                 localX = 85 - 2 * 40 = 5
           表示该区块在SCA文件内的第5个位置（范围：0-39）

        2. 负数坐标处理：负数区块坐标需要先正确计算区域坐标，再计算相对位置
           例如：区块坐标 chunkX=-5, scaPkgSize=40
           计算：regionX = (-5 + 1) / 40 - 1 = -1
                 localX = -5 - (-1) * 40 = -5 + 40 = 35
           表示该区块在SCA文件内的第35个位置

        3. 结果范围：SCA内坐标范围始终为 [0, scaPkgSize-1]，无论原始区块坐标的正负 */
        int regionX = x >= 0 ? x / scaPkgSize : (x + 1) / scaPkgSize - 1;
        int regionZ = z >= 0 ? z / scaPkgSize : (z + 1) / scaPkgSize - 1;
        
        int localX = x - regionX * scaPkgSize;
        int localZ = z - regionZ * scaPkgSize;
        
        return ScaLocalPos.of(localX, localZ);
    }

    @Override
    public String toString() {
        return "区块坐标:[" + x + "," + y + "," + z + "]";
    }


    /**
     * 获取区块缓存键值(原理: 将两个 32-bit 的 int 压缩到一个 64-bit 的 long 中)
     * @return 区块缓存键值
     */
    public long getChunkKey() {
        return ((long)x << 32) | (z & 0xFFFFFFFFL);
    }

    /**
     * 获取区块缓存键值(世界名称+区块坐标)
     * @param worldName 世界名称
     * @return 区块缓存键值
     */
    public String getChunkKey(String worldName){
        return worldName + "." + getChunkKey();
    }

}
