package com.ksptool.mycraft.client.entity;

import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * 客户端实体基类，用于存储从服务器同步的实体状态
 */
@Getter
public class ClientEntity {
    //唯一ID
    protected final UUID uniqueId;

    //位置
    protected final Vector3f position;
    
    //上一逻辑刻的位置（用于插值）
    protected final Vector3f previousPosition;

    public ClientEntity(UUID uniqueId) {
        this.uniqueId = uniqueId != null ? uniqueId : UUID.randomUUID();
        this.position = new Vector3f();
        this.previousPosition = new Vector3f();
    }

    /**
     * 更新实体位置（从服务器同步）
     */
    public void updatePosition(Vector3f newPosition) {
        previousPosition.set(position);
        position.set(newPosition);
    }
}

