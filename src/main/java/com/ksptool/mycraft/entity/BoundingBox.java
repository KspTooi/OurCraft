package com.ksptool.mycraft.entity;

import lombok.Getter;
import org.joml.Vector3f;

/**
 * 碰撞边界框类，用于实体碰撞检测
 */
@Getter
public class BoundingBox {
    private float minX;
    private float minY;
    private float minZ;
    private float maxX;
    private float maxY;
    private float maxZ;

    public BoundingBox(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    public BoundingBox(Vector3f position, float width, float height) {
        float halfWidth = width / 2.0f;
        this.minX = position.x - halfWidth;
        this.minY = position.y;
        this.minZ = position.z - halfWidth;
        this.maxX = position.x + halfWidth;
        this.maxY = position.y + height;
        this.maxZ = position.z + halfWidth;
    }

    public void update(Vector3f position) {
        float width = maxX - minX;
        float height = maxY - minY;
        float halfWidth = width / 2.0f;
        
        this.minX = position.x - halfWidth;
        this.minY = position.y;
        this.minZ = position.z - halfWidth;
        this.maxX = position.x + halfWidth;
        this.maxY = position.y + height;
        this.maxZ = position.z + halfWidth;
    }

    public BoundingBox offset(Vector3f offset) {
        return new BoundingBox(
            minX + offset.x, minY + offset.y, minZ + offset.z,
            maxX + offset.x, maxY + offset.y, maxZ + offset.z
        );
    }

    public boolean intersects(BoundingBox other) {
        return minX < other.maxX && maxX > other.minX &&
               minY < other.maxY && maxY > other.minY &&
               minZ < other.maxZ && maxZ > other.minZ;
    }

    public float getWidth() {
        return maxX - minX;
    }

    public float getHeight() {
        return maxY - minY;
    }

    public float getDepth() {
        return maxZ - minZ;
    }
}

