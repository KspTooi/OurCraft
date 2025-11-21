package com.ksptool.ourcraft.sharedcore.entity.inner;

import com.ksptool.ourcraft.server.entity.ServerEntity;
import com.ksptool.ourcraft.server.world.ServerWorld;
import com.ksptool.ourcraft.sharedcore.BoundingBox;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

/**
 * 生物实体基类，实现重力、物理运动和碰撞检测
 */
@Getter
public abstract class LivingEntity extends ServerEntity {

    //默认重力为 -20F
    protected static final float GRAVITY = -20.0f;

    //跳跃速度
    protected static final float JUMP_VELOCITY = 8.0f;

    //生命值
    protected float health = 40.0f;

    //最大生命值
    protected float maxHealth = 40.0f;

    //饥饿值
    protected float hunger = 40.0f;

    //最大饥饿值
    protected float maxHunger = 40.0f;

    //眼睛高度
    @Setter
    protected float eyeHeight = 1.6f;

    public LivingEntity(ServerWorld world) {
        super(world);
    }

    public LivingEntity(ServerWorld world, java.util.UUID uniqueId) {
        super(world, uniqueId);
    }

    @Override
    public void update(float delta) {
        handlePhysics(delta);
    }

    
    protected void handlePhysics(float delta) {
        if (delta <= 0) {
            return;
        }
        
        float clampedDelta = Math.min(delta, 0.1f);
        
        velocity.y += GRAVITY * clampedDelta;
        
        Vector3f movement = new Vector3f(velocity);
        movement.mul(clampedDelta);
        
        Vector3f newPosition = new Vector3f(position);
        
        if (boundingBox == null) {
            boundingBox = new BoundingBox(position, 0.6f, 1.8f);
        }
        
        // 服务端实体总是有world，执行完整的碰撞检测
        newPosition.x += movement.x;
        BoundingBox testBox = boundingBox.offset(new Vector3f(movement.x, 0, 0));
        if (!world.canMoveTo(testBox)) {
            newPosition.x = position.x;
            velocity.x = 0;
        }
        
        newPosition.z += movement.z;
        testBox = boundingBox.offset(new Vector3f(0, 0, movement.z));
        if (!world.canMoveTo(testBox)) {
            newPosition.z = position.z;
            velocity.z = 0;
        }
        
        newPosition.y += movement.y;
        testBox = boundingBox.offset(new Vector3f(0, movement.y, 0));
        if (!world.canMoveTo(testBox)) {
            if (movement.y < 0) {
                onGround = true;
            }
            velocity.y = 0;
            newPosition.y = position.y;
        }
        
        if (world.canMoveTo(testBox)) {
            onGround = false;
        }
        
        Vector3f oldPosition = new Vector3f(position);
        position.set(newPosition);
        if (boundingBox == null) {
            boundingBox = new BoundingBox(position, 0.6f, 1.8f);
        } else {
            boundingBox.update(position);
        }
        
        if (oldPosition.x != position.x || oldPosition.y != position.y || oldPosition.z != position.z) {
            markDirty(true);
        }
        
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
        if (this.health != health) {
            this.health = health;
            markDirty(true);
        }
    }

    public void setMaxHealth(float maxHealth) {
        if (this.maxHealth != maxHealth) {
            this.maxHealth = maxHealth;
            markDirty(true);
        }
    }

    public void setHunger(float hunger) {
        if (this.hunger != hunger) {
            this.hunger = hunger;
            markDirty(true);
        }
    }

    public void setMaxHunger(float maxHunger) {
        if (this.maxHunger != maxHunger) {
            this.maxHunger = maxHunger;
            markDirty(true);
        }
    }
}

