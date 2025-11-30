package com.ksptool.ourcraft.server.entity;

import com.ksptool.ourcraft.sharedcore.BoundingBox;
import com.ksptool.ourcraft.server.world.ServerWorld;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * 服务端生物实体基类，实现重力、物理运动和碰撞检测
 */
@Getter
public abstract class ServerLivingEntity extends ServerEntity {

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
    protected float eyeHeight = 1.7F;


    public ServerLivingEntity(ServerWorld world) {
        super(world);
    }

    public ServerLivingEntity(ServerWorld world, java.util.UUID uniqueId) {
        super(world, uniqueId);
    }

    @Override
    public void update(double delta) {
        handlePhysics(delta);
    }


    protected void handlePhysics(double delta) {
        //基础合法性检查
        if (delta <= 0) {
            return;
        }

        //防穿墙处理 (Lag Protection)
        //限制最大时间步长为 0.1秒。
        //解释：如果服务器卡顿导致 delta 很大（例如 1.0s），物体会一次移动很远从而穿过墙壁。
        //强制截断 delta 可以保证物理模拟的连续性和稳定性。
        double clampedDelta = Math.min(delta, 0.1f);

        //应用重力
        //根据时间流逝增加垂直向下的速度
        velocity.y += GRAVITY * clampedDelta;

        //计算本帧预期的位移向量 (速度 * 时间)
        Vector3d movement = new Vector3d(velocity);
        movement.mul(clampedDelta);

        //用于计算的新位置副本
        Vector3d newPosition = new Vector3d(position);

        //确保包围盒已初始化 (Lazy initialization)
        if (boundingBox == null) {
            boundingBox = new BoundingBox(position, 0.6f, 1.8f);
        }

        //分轴碰撞检测 (Separating Axis Collision) ---
        //关键逻辑：分别检测 X、Z、Y 轴。这样可以让实体贴着墙壁滑动，而不是一旦碰撞就完全卡住。
        //服务端实体总是有world，执行完整的碰撞检测

        //[X轴处理]
        newPosition.x += movement.x;
        //创建一个向 X 轴偏移的测试包围盒
        BoundingBox testBox = boundingBox.offset(new Vector3d(movement.x, 0, 0));
        //如果 X 轴方向有阻挡
        if (!world.canMoveTo(testBox)) {
            newPosition.x = position.x; // 回滚 X 轴位置
            velocity.x = 0;             // X 轴速度归零（撞墙停止）
        }

        //[Z轴处理]
        newPosition.z += movement.z;
        testBox = boundingBox.offset(new Vector3d(0, 0, movement.z));
        //如果 Z 轴方向有阻挡
        if (!world.canMoveTo(testBox)) {
            newPosition.z = position.z; // 回滚 Z 轴位置
            velocity.z = 0;             // Z 轴速度归零
        }

        //[Y轴处理] (处理落地或顶头)
        newPosition.y += movement.y;
        testBox = boundingBox.offset(new Vector3d(0, movement.y, 0));
        if (!world.canMoveTo(testBox)) {
            // 如果是向下运动且发生碰撞，说明落地了
            if (movement.y < 0) {
                onGround = true;
            }
            velocity.y = 0;             // Y 轴速度归零
            newPosition.y = position.y; // 回滚 Y 轴位置
        }

        // 再次检查当前位置是否可以移动（用于检测离开地面）
        if (world.canMoveTo(testBox)) {
            onGround = false; // 如果脚下没有方块，标记为滞空
        }

        //更新状态 ---
        Vector3f oldPosition = new Vector3f(position);
        position.set(newPosition); // 应用最终计算出的位置

        //同步更新包围盒位置
        if (boundingBox == null) {
            boundingBox = new BoundingBox(position, 0.6f, 1.8f);
        } else {
            boundingBox.update(position);
        }

        //如果位置发生了变化，标记脏数据（用于通知网络同步或触发区块加载）
        if (oldPosition.x != position.x || oldPosition.y != position.y || oldPosition.z != position.z) {
            markDirty(true);
        }

        //摩擦力/阻力应用 ---
        float groundFriction = 0.6f; // 地面摩擦力（数值越小，减速越快）
        float airFriction = 0.91f;   // 空气阻力（通常比地面摩擦力大，意味着减速慢）

        // 根据是否在地面应用不同的阻力系数
        // 注意：这里只衰减水平速度 (X, Z)，垂直速度由重力和碰撞控制
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

