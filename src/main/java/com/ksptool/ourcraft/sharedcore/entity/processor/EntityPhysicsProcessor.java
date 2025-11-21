package com.ksptool.ourcraft.sharedcore.entity.processor;


import com.ksptool.ourcraft.sharedcore.entity.SharedEntity;
import com.ksptool.ourcraft.sharedcore.entity.components.PhysicsComponent;
import com.ksptool.ourcraft.sharedcore.entity.components.TransformComponent;
import com.ksptool.ourcraft.sharedcore.world.SharedWorld;
import org.joml.Vector3d;

import java.util.List;

public class EntityPhysicsProcessor implements EntityProcessor {


    private final SharedWorld world;

    public EntityPhysicsProcessor(SharedWorld world) {
        this.world = world;
    }

    @Override
    public void process(SharedEntity entity, double delta) {

        PhysicsComponent phys = entity.getComponent(PhysicsComponent.class);
        TransformComponent trans = entity.getComponent(TransformComponent.class);

        //应用重力
        if (phys.isHasGravity()) {
            phys.getVelocity().y += phys.getGravityMultiplier() * delta;
        }

        //计算位移量
        Vector3d movement = new Vector3d(phys.getVelocity());
        movement.mul(delta);

        //记录旧位置 (用于插值)
        trans.getPrevPosition().set(trans.getPosition());

        //简化的碰撞检测与位置更新
        // 注意：这里需要将你的 BoundingBox 逻辑适配为 double，或者强转为 float
        if (phys.getBoundingBox() != null) {
            // 更新包围盒位置到当前实体位置
            // phys.getBoundingBox().update(trans.getPosition());

            // 这里调用你 World 类里的 collisionManager
            // moveEntityWithCollision(trans, phys, movement, world);

            // 临时逻辑：直接应用位置 (无碰撞)
            trans.getPosition().add(movement);

            // 地面检测模拟
            if (trans.getPosition().y < 0) { // 假设0是地面
                trans.getPosition().y = 0;
                phys.getVelocity().y = 0;
                phys.setOnGround(true);
            } else {
                phys.setOnGround(false);
            }
        } else {
            // 无碰撞箱实体（如幽灵模式）
            trans.getPosition().add(movement);
        }

        //应用摩擦力
        double friction = phys.isOnGround() ? 0.6 : 0.98; // 空中阻力小
        phys.getVelocity().x *= friction;
        phys.getVelocity().z *= friction;
    }

    @Override
    public void process(List<SharedEntity> entity, double delta) {

    }
}