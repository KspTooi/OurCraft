package com.ksptool.ourcraft.sharedcore;

import lombok.Getter;
import org.joml.Vector3d;

/**
 * 碰撞边界框类，用于实体碰撞检测
 */
@Getter
public class BoundingBox {

    //X轴最小值
    private double minX;

    //Y轴最小值
    private double minY;

    //Z轴最小值
    private double minZ;

    //X轴最大值
    private double maxX;

    //Y轴最大值
    private double maxY;

    //Z轴最大值
    private double maxZ;


    public BoundingBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    public BoundingBox(Vector3d position, double width, double height) {
        double halfWidth = width / 2.0;
        this.minX = position.x - halfWidth;
        this.minY = position.y;
        this.minZ = position.z - halfWidth;
        this.maxX = position.x + halfWidth;
        this.maxY = position.y + height;
        this.maxZ = position.z + halfWidth;
    }

    /**
     * 根据新位置更新边界框，保持宽度和高度不变
     * @param position 新的位置
     */
    public void update(Vector3d position) {
        double width = maxX - minX;
        double height = maxY - minY;
        double halfWidth = width / 2.0;
        
        this.minX = position.x - halfWidth;
        this.minY = position.y;
        this.minZ = position.z - halfWidth;
        this.maxX = position.x + halfWidth;
        this.maxY = position.y + height;
        this.maxZ = position.z + halfWidth;
    }

    /**
     * 返回一个偏移后的新边界框
     * @param offset 偏移量
     * @return 偏移后的新边界框
     */
    public BoundingBox offset(Vector3d offset) {
        return new BoundingBox(
            minX + offset.x, minY + offset.y, minZ + offset.z,
            maxX + offset.x, maxY + offset.y, maxZ + offset.z
        );
    }

    /**
     * 检测两个边界框是否相交
     * @param other 另一个边界框
     * @return 如果相交返回true，否则返回false
     */
    public boolean intersects(BoundingBox other) {
        return minX < other.maxX && maxX > other.minX &&
               minY < other.maxY && maxY > other.minY &&
               minZ < other.maxZ && maxZ > other.minZ;
    }

    public double getWidth() {
        return maxX - minX;
    }

    public double getHeight() {
        return maxY - minY;
    }

    public double getDepth() {
        return maxZ - minZ;
    }
}

