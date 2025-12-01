package com.ksptool.ourcraft.sharedcore.utils.position;

import java.util.Objects;

import lombok.Getter;

/**
 * SCA文件坐标系
 * 
 * 坐标系 SCA文件坐标系: 表示某个SCA文件在世界网格中的位置（类似于Region坐标）
 * 例如：S.0.0.sca, S.-1.2.sca
 * 该类实现安全的相等性判断和哈希码计算 可以用作Map的Key
 */
@Getter
public class ScaPos {

    //SCA文件的X坐标（Region X）
    private final int x;

    //SCA文件的Z坐标（Region Z）
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
     * 创建一个SCA文件位置
     * @param x SCA X坐标
     * @param z SCA Z坐标
     * @return SCA文件位置
     */
    public static ScaPos of(int x, int z) {
        return new ScaPos(x, z);
    }

    /**
     * 转换为SCA文件名
     * @return SCA文件名 如S.1.1.sca
     */
    public String toScaFileName() {
        return "S." + x + "." + z + ".sca";
    }
    
    @Override
    public String toString() {
        return "SCA文件坐标:[" + x + "," + z + "]";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        ScaPos other = (ScaPos) obj;
        return x == other.x && z == other.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, z);
    }
}
