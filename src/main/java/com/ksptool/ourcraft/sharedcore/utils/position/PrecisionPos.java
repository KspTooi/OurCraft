package com.ksptool.ourcraft.sharedcore.utils.position;

import java.util.Objects;

import org.joml.Vector3d;

import lombok.Getter;

/**
 * 高精度位置类
 * 用于表示一个世界中的高精度坐标(例如实体位置，它们不一定是网格对齐的，通常需要更高的精度来表示)
 * 该类实现安全的相等性判断和哈希码计算 可以用作Map的Key
 * 
 * 注意：虽然实现了equals/hashCode，但由于使用double精度，建议谨慎作为Map的Key使用
 * 如需作为Key，建议先转换为Pos或ChunkPos
 */
@Getter
public class PrecisionPos {

    private final double x;
    private final double y;
    private final double z;

    /**
     * 构造函数
     * @param x X坐标
     * @param y Y坐标
     * @param z Z坐标
     */
    private PrecisionPos(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * 创建一个高精度位置
     * @param x X坐标
     * @param y Y坐标
     * @param z Z坐标
     * @return 高精度位置
     */
    public static PrecisionPos of(double x, double y, double z) {
        return new PrecisionPos(x, y, z);
    }

    /**
     * 创建一个高精度位置
     * @param position 位置向量
     * @return 高精度位置
     */
    public static PrecisionPos of(Vector3d position) {
        if (position == null) {
            throw new IllegalArgumentException("位置向量不能为空");
        }
        return new PrecisionPos(position.x, position.y, position.z);
    }

    /**
     * 转换世界坐标为区块坐标（指定区块大小）
     * 此方法直接使用double精度计算，避免截断错误
     * 这是PrecisionPos相比Pos的主要优势：可以正确处理实体在区块边界附近的位置
     * 
     * @param chunkSizeX 区块X轴大小（通常为16）
     * @param chunkSizeZ 区块Z轴大小（通常为16）
     * @return 区块坐标对象
     * @throws IllegalArgumentException 如果区块大小小于等于0
     */
    public ChunkPos toChunkPos(int chunkSizeX, int chunkSizeZ) {
        if (chunkSizeX <= 0 || chunkSizeZ <= 0) {
            throw new IllegalArgumentException("区块大小必须大于0: chunkSizeX=" + chunkSizeX + ", chunkSizeZ=" + chunkSizeZ);
        }

        /*
        计算与转换说明：
        1. 区块坐标计算原理：将世界坐标除以区块大小并向下取整
           例如：世界坐标 x=15.9, chunkSizeX=16
           计算：15.9 / 16 = 0.99375，向下取整得到 chunkX = 0
           世界坐标 x=16.1, chunkSizeX=16
           计算：16.1 / 16 = 1.00625，向下取整得到 chunkX = 1
           这样可以正确检测实体跨区块边界的情况

        2. 负数坐标处理：使用 Math.floor 确保负数坐标正确向下取整
           例如：世界坐标 x=-0.1, chunkSizeX=16
           计算：-0.1 / 16 = -0.00625，向下取整得到 chunkX = -1

        3. 精度优势：直接使用double精度，不会像Pos.of(Vector3d)那样先截断为int
           这确保了实体在区块边界附近（如15.9 -> 16.1）时能正确检测到区块变化

        4. Y坐标保留：虽然区块坐标主要使用 X 和 Z，但 Y 坐标会原样保留（截断为int）
           用于后续可能的垂直区块划分或其他用途 */
        int chunkX = (int) Math.floor(x / chunkSizeX);
        int chunkZ = (int) Math.floor(z / chunkSizeZ);
        
        //虽然区块坐标仅使用 X 和 Z,但从World坐标转换为Chunk坐标时,Y坐标也会保留
        return ChunkPos.of(chunkX, (int) y, chunkZ);
    }

    /**
     * 转换世界坐标为区块内局部坐标（指定区块大小）
     * @param chunkSizeX 区块X轴大小（通常为16）
     * @param chunkSizeZ 区块Z轴大小（通常为16）
     * @return 区块内局部坐标对象
     * @throws IllegalArgumentException 如果区块大小小于等于0
     */
    public ChunkLocalPos toLocalPos(int chunkSizeX, int chunkSizeZ) {
        if (chunkSizeX <= 0 || chunkSizeZ <= 0) {
            throw new IllegalArgumentException("区块大小必须大于0: chunkSizeX=" + chunkSizeX + ", chunkSizeZ=" + chunkSizeZ);
        }

        /*
        计算与转换说明：
        1. 局部坐标计算原理：先计算区块坐标，然后用世界坐标减去区块原点坐标
           例如：世界坐标 x=35.7, chunkSizeX=16
           计算：chunkX = floor(35.7/16) = 2
                 localX = 35 - 2*16 = 3（注意：这里会截断为int）
           表示该坐标在区块内的第3个位置（范围：0-15）

        2. 精度说明：局部坐标最终会截断为int，因为ChunkLocalPos使用int
           如果需要保留小数部分，可能需要创建DoubleChunkLocalPos类 */
        int chunkX = (int) Math.floor(x / chunkSizeX);
        int chunkZ = (int) Math.floor(z / chunkSizeZ);
        
        int localX = (int) x - chunkX * chunkSizeX;
        int localZ = (int) z - chunkZ * chunkSizeZ;
        
        return ChunkLocalPos.of(localX, (int) y, localZ);
    }

    /**
     * 转换世界坐标为SCA封装坐标（指定SCA封装大小和区块大小）
     * @param scaPkgSize SCA封装大小（通常为40 即一个SCA文件封装40X40个区块）
     * @param chunkSizeX 区块X轴大小（通常为16）
     * @param chunkSizeZ 区块Z轴大小（通常为16）
     * @return SCA包坐标对象
     * @throws IllegalArgumentException 如果SCA包大小或区块大小小于等于0
     */
    public ScaPos toScaPos(int scaPkgSize, int chunkSizeX, int chunkSizeZ) {
        if (scaPkgSize <= 0) {
            throw new IllegalArgumentException("SCA封装大小必须大于0: scaPkgSize=" + scaPkgSize);
        }
        if (chunkSizeX <= 0 || chunkSizeZ <= 0) {
            throw new IllegalArgumentException("区块大小必须大于0: chunkSizeX=" + chunkSizeX + ", chunkSizeZ=" + chunkSizeZ);
        }

        // 第一步：世界坐标转换为区块坐标（使用高精度）
        int chunkX = (int) Math.floor(x / chunkSizeX);
        int chunkZ = (int) Math.floor(z / chunkSizeZ);
        
        // 第二步：区块坐标转换为SCA坐标
        int regionX = chunkX >= 0 ? chunkX / scaPkgSize : (chunkX + 1) / scaPkgSize - 1;
        int regionZ = chunkZ >= 0 ? chunkZ / scaPkgSize : (chunkZ + 1) / scaPkgSize - 1;
        
        return ScaPos.of(regionX, regionZ);
    }

    /**
     * 转换为整数Pos（截断）
     * @return 整数位置对象
     */
    public Pos toIntPos() {
        return Pos.of((int) x, (int) y, (int) z);
    }

    /**
     * 转换为Vector3d
     * @return 位置向量
     */
    public Vector3d toVector3d() {
        return new Vector3d(x, y, z);
    }

    @Override
    public String toString() {
        return "世界坐标(高精度):[" + x + "," + y + "," + z + "]";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        PrecisionPos other = (PrecisionPos) obj;
        // 使用Double.compare处理NaN和精度问题
        return Double.compare(x, other.x) == 0 
            && Double.compare(y, other.y) == 0 
            && Double.compare(z, other.z) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }

}
