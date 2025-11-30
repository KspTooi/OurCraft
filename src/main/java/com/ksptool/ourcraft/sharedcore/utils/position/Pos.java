package com.ksptool.ourcraft.sharedcore.utils.position;

import org.joml.Vector3d;

import lombok.Getter;

/**
 * 位置类
 * 坐标系 世界坐标系: 用于表示一个世界中的绝对坐标
 */
@Getter
public class Pos {

    private final int x;
    private final int y;
    private final int z;

    /**
     * 构造函数
     * @param x X坐标
     * @param y Y坐标
     * @param z Z坐标
     */
    private Pos(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * 创建一个位置
     * @param x X坐标
     * @param y Y坐标
     * @param z Z坐标
     * @return 位置
     */
    public static Pos of(int x, int y, int z) {
        return new Pos(x, y, z);
    }

    /**
     * 创建一个位置
     * @param x X坐标
     * @param y Y坐标
     * @param z Z坐标
     * @return 位置
     */
    public static Pos of(double x, double y, double z) {
        return new Pos((int) x, (int) y, (int) z);
    }

    /**
     * 创建一个位置
     * @param position 位置
     * @return 位置
     */
    public static Pos of(Vector3d position) {
        return new Pos((int) position.x, (int) position.y, (int) position.z);
    }

    /**
     * 转换世界坐标为区块坐标（指定区块大小）
     * 此方法允许指定自定义的区块大小，适用于使用非标准区块大小的世界模板。
     * @param chunkSizeX 区块X轴大小（通常为16）
     * @param chunkSizeZ 区块Z轴大小（通常为16）
     * @return 区块坐标对象
     * @throws IllegalArgumentException 如果区块大小小于等于0
     */
    public ChunkPos toChunkPos(int chunkSizeX, int chunkSizeZ) {

        // 检查区块大小是否大于0
        if (chunkSizeX <= 0 || chunkSizeZ <= 0) {
            throw new IllegalArgumentException("区块大小必须大于0: chunkSizeX=" + chunkSizeX + ", chunkSizeZ=" + chunkSizeZ);
        }

        /*
        计算与转换说明：
        1. 区块坐标计算原理：将世界坐标除以区块大小并向下取整
        例如：世界坐标 x=35, chunkSizeX=16
        计算：35 / 16 = 2.1875，向下取整得到 chunkX = 2
        表示该坐标位于第2个区块（区块范围：32-47）

        2. 负数坐标处理：使用 Math.floor 确保负数坐标正确向下取整
        例如：世界坐标 x=-5, chunkSizeX=16
        计算：-5 / 16 = -0.3125，向下取整得到 chunkX = -1
        如果使用简单除法 -5 / 16 = 0（错误），Math.floor 得到 -1（正确）

        3. 精度处理：先转换为 double 类型进行除法运算，避免整数除法丢失小数部分
        然后使用 Math.floor 向下取整，最后转换为 int 类型

        4. Y坐标保留：虽然区块坐标主要使用 X 和 Z，但 Y 坐标会原样保留
        用于后续可能的垂直区块划分或其他用途 */
        int chunkX = (int) Math.floor((double) x / chunkSizeX);
        int chunkZ = (int) Math.floor((double) z / chunkSizeZ);
        
        //虽然区块坐标仅使用 X 和 Z,但从World坐标转换为Chunk坐标时,Y坐标也会保留
        return ChunkPos.of(chunkX,y, chunkZ);
    }

    /**
     * 转换世界坐标为区块内局部坐标（指定区块大小）
     * 此方法允许指定自定义的区块大小，适用于使用非标准区块大小的世界模板。
     * @param chunkSizeX 区块X轴大小（通常为16）
     * @param chunkSizeZ 区块Z轴大小（通常为16）
     * @return 区块内局部坐标对象
     * @throws IllegalArgumentException 如果区块大小小于等于0
     */
    public ChunkLocalPos toLocalPos(int chunkSizeX, int chunkSizeZ) {

        // 检查区块大小是否大于0
        if (chunkSizeX <= 0 || chunkSizeZ <= 0) {
            throw new IllegalArgumentException("区块大小必须大于0: chunkSizeX=" + chunkSizeX + ", chunkSizeZ=" + chunkSizeZ);
        }

        /*
        计算与转换说明：
        1. 局部坐标计算原理：先计算区块坐标，然后用世界坐标减去区块原点坐标
           公式：localX = worldX - chunkX * chunkSizeX
           例如：世界坐标 x=35, chunkSizeX=16
           计算：chunkX = floor(35/16) = 2
                 localX = 35 - 2*16 = 35 - 32 = 3
           表示该坐标在区块内的第3个位置（范围：0-15）

        2. 负数坐标处理：使用 Math.floor 确保负数坐标正确计算区块坐标
           例如：世界坐标 x=-5, chunkSizeX=16
           计算：chunkX = floor(-5/16) = -1
                 localX = -5 - (-1)*16 = -5 + 16 = 11
           表示该坐标在区块-1内的第11个位置

        3. 精度处理：先转换为 double 类型进行除法运算，避免整数除法丢失小数部分
           然后使用 Math.floor 向下取整，最后转换为 int 类型

        4. Y坐标保留：Y坐标直接保留，因为区块通常不进行垂直划分
           局部坐标的Y值等于世界坐标的Y值 */
        int chunkX = (int) Math.floor((double) x / chunkSizeX);
        int chunkZ = (int) Math.floor((double) z / chunkSizeZ);
        
        int localX = x - chunkX * chunkSizeX;
        int localZ = z - chunkZ * chunkSizeZ;
        
        return ChunkLocalPos.of(localX, y, localZ);
    }

    /**
     * 转换世界坐标为SCA封装坐标（指定SCA封装大小和区块大小）
     * 此方法先转换为区块坐标，再转换为SCA坐标，适用于需要从世界坐标直接获取SCA文件位置的场景。
     * @param scaPkgSize SCA封装大小（通常为40 即一个SCA文件封装40X40个区块）
     * @param chunkSizeX 区块X轴大小（通常为16）
     * @param chunkSizeZ 区块Z轴大小（通常为16）
     * @return SCA包坐标对象
     * @throws IllegalArgumentException 如果SCA包大小或区块大小小于等于0
     */
    public ScaPos toScaPos(int scaPkgSize, int chunkSizeX, int chunkSizeZ) {

        // 检查SCA封装大小是否大于0
        if (scaPkgSize <= 0) {
            throw new IllegalArgumentException("SCA封装大小必须大于0: scaPkgSize=" + scaPkgSize);
        }

        // 检查区块大小是否大于0
        if (chunkSizeX <= 0 || chunkSizeZ <= 0) {
            throw new IllegalArgumentException("区块大小必须大于0: chunkSizeX=" + chunkSizeX + ", chunkSizeZ=" + chunkSizeZ);
        }

        /*
        计算与转换说明：
        1. 转换流程：世界坐标 → 区块坐标 → SCA坐标
           第一步：计算区块坐标
           例如：世界坐标 x=640, chunkSizeX=16
           计算：chunkX = floor(640/16) = 40
           第二步：计算SCA坐标
           例如：区块坐标 chunkX=40, scaPkgSize=40
           计算：regionX = 40 / 40 = 1
           表示该坐标位于第1个SCA文件（区块范围：40-79）

        2. 负数坐标处理：两个步骤都需要正确处理负数
           第一步：使用 Math.floor 确保负数世界坐标正确转换为负数区块坐标
           第二步：使用特殊公式确保负数区块坐标正确转换为负数区域坐标
           例如：世界坐标 x=-80, chunkSizeX=16, scaPkgSize=40
           计算：chunkX = floor(-80/16) = -5
                 regionX = (-5 + 1) / 40 - 1 = -1

        3. 精度处理：先转换为 double 类型进行除法运算，避免整数除法丢失小数部分
           然后使用 Math.floor 向下取整，最后转换为 int 类型

        4. SCA坐标仅使用X和Z：SCA坐标表示区域文件位置，不包含Y坐标信息 */
        // 第一步：世界坐标转换为区块坐标
        int chunkX = (int) Math.floor((double) x / chunkSizeX);
        int chunkZ = (int) Math.floor((double) z / chunkSizeZ);
        
        // 第二步：区块坐标转换为SCA坐标
        int regionX = chunkX >= 0 ? chunkX / scaPkgSize : (chunkX + 1) / scaPkgSize - 1;
        int regionZ = chunkZ >= 0 ? chunkZ / scaPkgSize : (chunkZ + 1) / scaPkgSize - 1;
        
        return ScaPos.of(regionX, regionZ);
    }


    @Override
    public String toString() {
        return "世界坐标:[" + x + "," + y + "," + z + "]";
    }


}
