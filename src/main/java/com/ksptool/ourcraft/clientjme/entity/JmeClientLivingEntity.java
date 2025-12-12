package com.ksptool.ourcraft.clientjme.entity;

import com.ksptool.ourcraft.clientjme.world.JmeClientCollisionManager;
import com.ksptool.ourcraft.clientjme.world.JmeClientWorld;
import com.ksptool.ourcraft.sharedcore.BoundingBox;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * 客户端生物实体基类，实现重力、物理运动和碰撞检测（用于客户端预测）
 */
@Getter
public abstract class JmeClientLivingEntity extends JmeClientEntity {

    //默认重力为 -20F
    protected static final float GRAVITY = -20.0f;

    //跳跃速度
    protected static final float JUMP_VELOCITY = 8.0f;

    //速度
    protected final Vector3d velocity;

    //是否在地面上
    @Setter
    protected boolean onGround;

    //边界框
    @Setter
    protected BoundingBox boundingBox;

    //眼睛高度
    @Setter
    protected float eyeHeight = 1.6f;

    //生命值
    protected float health = 40.0f;

    //最大生命值
    protected float maxHealth = 40.0f;

    //饥饿值
    protected float hunger = 40.0f;

    //最大饥饿值
    protected float maxHunger = 40.0f;

    //客户端世界（用于碰撞检测）
    protected JmeClientWorld world;

    //碰撞管理器
    protected JmeClientCollisionManager collisionManager;

    public JmeClientLivingEntity(UUID uniqueId, JmeClientWorld world) {
        super(uniqueId);
        this.world = world;
        this.velocity = new Vector3d();
        this.onGround = false;
        if (world != null) {
            this.collisionManager = new JmeClientCollisionManager(world);
        }
    }

    @Override
    public void update(float delta) {
        handlePhysics(delta);
    }

    /**
     * 处理物理更新（客户端预测）
     */
    protected void handlePhysics(float delta) {
        if (delta <= 0) {
            return;
        }

        if (world == null || collisionManager == null) {
            return;
        }

        float clampedDelta = Math.min(delta, 0.1f);

        velocity.y += GRAVITY * clampedDelta;

        Vector3f movement = new Vector3f(velocity);
        movement.mul(clampedDelta);

        Vector3d newPosition = new Vector3d(position);

        if (boundingBox == null) {
            boundingBox = new BoundingBox(position, 0.6f, 1.8f);
        }

        // X轴移动
        newPosition.x += movement.x;
        BoundingBox testBox = boundingBox.offset(new Vector3d(movement.x, 0, 0));
        if (!collisionManager.canMoveTo(testBox)) {
            newPosition.x = position.x;
            velocity.x = 0;
        }

        // Z轴移动
        newPosition.z += movement.z;
        testBox = boundingBox.offset(new Vector3d(0, 0, movement.z));
        if (!collisionManager.canMoveTo(testBox)) {
            newPosition.z = position.z;
            velocity.z = 0;
        }

        // Y轴移动
        newPosition.y += movement.y;
        testBox = boundingBox.offset(new Vector3d(0, movement.y, 0));
        if (!collisionManager.canMoveTo(testBox)) {
            if (movement.y < 0) {
                onGround = true;
            }
            velocity.y = 0;
            newPosition.y = position.y;
        }

        if (collisionManager.canMoveTo(testBox)) {
            onGround = false;
        }

        Vector3d oldPosition = new Vector3d(position);
        previousPosition.set(position);
        position.set(newPosition);

        if (boundingBox == null) {
            boundingBox = new BoundingBox(position, 0.6f, 1.8f);
        } else {
            boundingBox.update(position);
        }

        // 应用摩擦力
        float groundFriction = 0.6f;
        float airFriction = 0.91f;

        if (onGround) {
            velocity.x *= groundFriction;
            velocity.z *= groundFriction;
        } else {
            velocity.x *= airFriction;
            velocity.z *= airFriction;
        }
    }

    public void setHealth(float health) {
        this.health = health;
    }

    public void setMaxHealth(float maxHealth) {
        this.maxHealth = maxHealth;
    }

    public void setHunger(float hunger) {
        this.hunger = hunger;
    }

    public void setMaxHunger(float maxHunger) {
        this.maxHunger = maxHunger;
    }
}

