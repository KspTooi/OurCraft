package com.ksptool.ourcraft.sharedcore.utils.position;

import java.util.Objects;

import lombok.Getter;

/**
 * SCA文件内局部区块坐标系
 * 
 * 坐标系 SCA内部坐标系: 用于表示"这个区块"在SCA文件内部的相对坐标 (通常范围 0-39)
 * 该类实现安全的相等性判断和哈希码计算 可以用作Map的Key
 */
@Getter
public class ScaLocalPos {

    //SCA内部区块X坐标
    private final int x;
    
    //SCA内部区块Z坐标
    private final int z;

    private ScaLocalPos(int x, int z) {
        this.x = x;
        this.z = z;
    }

    /**
     * 创建一个SCA内部局部位置
     * @param x SCA内部X坐标
     * @param z SCA内部Z坐标
     * @return SCA内部局部位置
     */
    public static ScaLocalPos of(int x, int z) {
        return new ScaLocalPos(x, z);
    }

    /**
     * 验证坐标是否在有效范围内
     * @param scaPackageSize SCA封装大小
     * @return 是否有效
     */
    public boolean isValid(int scaPackageSize) {
        if (scaPackageSize <= 0) {
            return false;
        }
        return x >= 0 && x < scaPackageSize && z >= 0 && z < scaPackageSize;
    }

    /**
     * 转换为索引表中的索引位置
     * 索引计算公式：index = z * scaPackageSize + x
     * @param scaPackageSize SCA封装大小
     * @return 索引位置
     */
    public int toIndex(int scaPackageSize) {
        if (scaPackageSize <= 0) {
            throw new IllegalArgumentException("SCA封装大小必须大于0: scaPackageSize=" + scaPackageSize);
        }
        return z * scaPackageSize + x;
    }

    /**
     * 转换为索引表中的索引条目偏移量
     * 偏移量计算公式：offset = headerOffset + index * headerIndexEntrySize
     * @param headerOffset 头部索引条目偏移量(魔数4B + 版本1B = 5B)
     * @param headerIndexEntrySize 头部索引条目长度(Offset和Length各4字节)
     * @param scaPackageSize SCA封装大小
     * @return 索引偏移量
     */
    public long toIndexOffset(int headerOffset, int headerIndexEntrySize, int scaPackageSize) {
        if (headerOffset < 0) {
            throw new IllegalArgumentException("头部索引条目偏移量不能为负: headerOffset=" + headerOffset);
        }
        if (headerIndexEntrySize <= 0) {
            throw new IllegalArgumentException("头部索引条目长度必须大于0: headerIndexEntrySize=" + headerIndexEntrySize);
        }
        
        return headerOffset + ((long) toIndex(scaPackageSize) * headerIndexEntrySize);
    }

    @Override
    public String toString() {
        return "SCA内部坐标:[" + x + "," + z + "]";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        ScaLocalPos other = (ScaLocalPos) obj;
        return x == other.x && z == other.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, z);
    }
}
