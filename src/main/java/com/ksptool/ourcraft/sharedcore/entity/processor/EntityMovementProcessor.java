package com.ksptool.ourcraft.sharedcore.entity.processor;

import com.ksptool.ourcraft.sharedcore.entity.SharedEntity;
import com.ksptool.ourcraft.sharedcore.entity.components.InputComponent;
import com.ksptool.ourcraft.sharedcore.entity.components.MoveComponent;
import com.ksptool.ourcraft.sharedcore.entity.components.PhysicsComponent;
import com.ksptool.ourcraft.sharedcore.entity.components.TransformComponent;
import com.ksptool.ourcraft.sharedcore.world.SharedWorld;
import org.joml.Vector3d;

import java.util.List;

public class EntityMovementProcessor implements EntityProcessor {

    private final SharedWorld world;

    public EntityMovementProcessor(SharedWorld world) {
        this.world = world;
    }

    @Override
    public void process(SharedEntity entity, double delta) {

        if (entity.hasComponent(InputComponent.class)) {
            return;
        }
        if (entity.hasComponent(MoveComponent.class)) {
            return;
        }
        if (entity.hasComponent(PhysicsComponent.class)) {
            return;
        }
        if (entity.hasComponent(TransformComponent.class)) {
            return;
        }

        InputComponent input = entity.getComponent(InputComponent.class);
        MoveComponent stats = entity.getComponent(MoveComponent.class);
        PhysicsComponent phys = entity.getComponent(PhysicsComponent.class);
        TransformComponent trans = entity.getComponent(TransformComponent.class);

        //计算期望的移动方向
        Vector3d wishDir = new Vector3d();
        double yawRad = Math.toRadians(trans.getYaw());

        if (input.isW()) {
            wishDir.x += Math.sin(yawRad);
            wishDir.z -= Math.cos(yawRad);
        }
        if (input.isS()) {
            wishDir.x -= Math.sin(yawRad);
            wishDir.z += Math.cos(yawRad);
        }
        if (input.isA()) {
            wishDir.x -= Math.cos(yawRad);
            wishDir.z -= Math.sin(yawRad);
        }
        if (input.isD()) {
            wishDir.x += Math.cos(yawRad);
            wishDir.z += Math.sin(yawRad);
        }

        //归一化并应用速度
        if (wishDir.lengthSquared() > 0) {
            wishDir.normalize();

            // 区分Sprint和Walk
            double targetSpeed = input.isSneak() ? stats.getMaxWalkSpeed() * 0.3 : stats.getMaxWalkSpeed();

            // 这里使用简化的加速度逻辑 (直接修改物理组件的速度)
            // 注意：我们只修改水平速度 (X, Z)，保留 Y 轴速度给物理引擎处理重力
            double acceleration = phys.isOnGround() ? stats.getGroundAcceleration() : stats.getAirAcceleration();

            phys.getVelocity().x += wishDir.x * acceleration * delta;
            phys.getVelocity().z += wishDir.z * acceleration * delta;

            // 简单的限速逻辑（防止无限加速）
            // 实际项目中可能需要更复杂的摩擦力计算
        }

        //处理跳跃
        if (input.isJump() && phys.isOnGround()) {
            phys.getVelocity().y = stats.getJumpVelocity();
            phys.setOnGround(false);
        }

    }

    @Override
    public void process(List<SharedEntity> entity, double delta) {
        for (SharedEntity e : entity) {
            process(e, delta);
        }
    }


}
