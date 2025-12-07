package com.ksptool.ourcraft.clientjme.entity;

import lombok.Getter;
import org.joml.Vector3d;

import java.util.UUID;

/**
 * 客户端实体基类，用于存储从服务器同步的实体状态
 */
@Getter
public class JmeClientEntity {
    //唯一ID
    protected final UUID uniqueId;

    //位置
    protected final Vector3d position;
    
    //上一逻辑刻的位置（用于插值）
    protected final Vector3d previousPosition;

    public JmeClientEntity(UUID uniqueId) {
        this.uniqueId = uniqueId != null ? uniqueId : UUID.randomUUID();
        this.position = new Vector3d();
        this.previousPosition = new Vector3d();
    }

    /**
     * 更新实体位置（从服务器同步）
     */
    public void updatePosition(Vector3d newPosition) {
        previousPosition.set(position);
        position.set(newPosition);
    }
    
    /**
     * 设置实体位置（用于初始化）
     * 如果previousPosition尚未被有意义地设置过，则同时设置它
     */
    public void setPosition(Vector3d newPosition) {
        position.set(newPosition);
        // 如果previousPosition尚未被有意义地设置过（仍为0,0,0），则用当前位置初始化它
        // 避免(0,0,0)的初始值导致错误的插值
        if (previousPosition.lengthSquared() < 0.001f) {
            previousPosition.set(newPosition);
        }
    }

    /**
     * 更新实体（子类可以重写以实现物理模拟等）
     */
    public void update(float delta) {
        // 默认实现为空，子类可以重写
    }
}

