package com.ksptool.ourcraft.client.entity;

import com.ksptool.ourcraft.client.Input;
import com.ksptool.ourcraft.client.item.ClientInventory;
import com.ksptool.ourcraft.client.world.ClientWorld;
import com.ksptool.ourcraft.sharedcore.events.PlayerInputEvent;
import com.ksptool.ourcraft.sharedcore.events.PlayerUpdateEvent;
import lombok.Getter;
import org.joml.Vector2d;
import org.joml.Vector2f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * 客户端玩家实体类，负责渲染和输入处理，支持客户端预测
 */
@Getter
public class ClientPlayer extends ClientLivingEntity {
    //相机
    private final Camera camera;

    //背包
    private final ClientInventory inventory;

    //鼠标灵敏度
    private float mouseSensitivity = 0.1f;
    
    //相机朝向
    private float yaw = 0.0f;
    private float pitch = 0.0f;
    private float previousYaw = 0.0f;
    private float previousPitch = 0.0f;
    
    //地面加速度
    private static final float GROUND_ACCELERATION = 40F;
    
    //空中加速度
    private static final float AIR_ACCELERATION = 5F;
    
    //最大移动速度
    private static final float MAX_SPEED = 40F;
    
    //生命值（从服务器同步）
    private float health = 40.0f;
    private float maxHealth = 40.0f;
    
    //饥饿值（从服务器同步）
    private float hunger = 40.0f;
    private float maxHunger = 40.0f;

    public ClientPlayer(ClientWorld world) {
        super(java.util.UUID.randomUUID(), world);
        this.camera = new Camera();
        this.inventory = new ClientInventory();
        this.eyeHeight = 1.6f;
        this.boundingBox = new com.ksptool.ourcraft.sharedcore.BoundingBox(position, 0.6f, 1.8f);
    }

    public ClientPlayer(UUID uniqueId, ClientWorld world) {
        super(uniqueId, world);
        this.camera = new Camera();
        this.inventory = new ClientInventory();
        this.eyeHeight = 1.6f;
        this.boundingBox = new com.ksptool.ourcraft.sharedcore.BoundingBox(position, 0.6f, 1.8f);
    }

    public void initializeCamera() {
        camera.setYaw(0);
        camera.setPitch(0);
        updateCamera();
    }

    @Override
    public void update(float delta) {
        // 限制delta值，防止极端时间间隔导致的问题
        if (delta > 0.1f) {
            delta = 0.1f;
        }
        
        // 更新previousYaw和previousPitch用于插值
        previousYaw = yaw;
        previousPitch = pitch;
        
        // 调用父类的物理更新
        super.update(delta);
        
        updateCamera();
    }
    
    /**
     * 处理鼠标输入（客户端本地处理）
     */
    public void handleMouseInput(Input input) {
        if (!input.isMouseLocked()) {
            return;
        }

        Vector2d mouseDelta = input.getMouseDelta();
        if (mouseDelta.x != 0 || mouseDelta.y != 0) {
            float deltaYaw = (float) mouseDelta.x * mouseSensitivity;
            float deltaPitch = (float) mouseDelta.y * mouseSensitivity;

            yaw += deltaYaw;
            pitch += deltaPitch;
            pitch = Math.max(-90, Math.min(90, pitch));
            
            camera.setYaw(yaw);
            camera.setPitch(pitch);
        }
    }
    
    /**
     * 应用玩家输入（移动方向）- 客户端预测
     */
    public void applyInput(PlayerInputEvent event) {
        Vector3f moveDirection = new Vector3f();
        float yawRad = (float) Math.toRadians(yaw);
        
        if (event.isForward()) {
            moveDirection.x += Math.sin(yawRad);
            moveDirection.z -= Math.cos(yawRad);
        }
        if (event.isBackward()) {
            moveDirection.x -= Math.sin(yawRad);
            moveDirection.z += Math.cos(yawRad);
        }
        if (event.isLeft()) {
            moveDirection.x -= Math.cos(yawRad);
            moveDirection.z -= Math.sin(yawRad);
        }
        if (event.isRight()) {
            moveDirection.x += Math.cos(yawRad);
            moveDirection.z += Math.sin(yawRad);
        }
        
        if (moveDirection.length() > 0) {
            moveDirection.normalize();
            
            float acceleration = onGround ? GROUND_ACCELERATION : AIR_ACCELERATION;
            float tickDelta = 1.0f / 20.0f; // 假设20 TPS，后续可以从WorldTemplate获取
            if (world != null && world.getTemplate() != null) {
                tickDelta = 1.0f / world.getTemplate().getTps();
            }
            velocity.x += moveDirection.x * acceleration * tickDelta;
            velocity.z += moveDirection.z * acceleration * tickDelta;
            
            Vector2d horizontalVelocity = new Vector2d(velocity.x, velocity.z);
            if (horizontalVelocity.lengthSquared() > MAX_SPEED * MAX_SPEED) {
                horizontalVelocity.normalize().mul(MAX_SPEED);
                velocity.x = horizontalVelocity.x;
                velocity.z = horizontalVelocity.y;
            }
        }
        
        if (event.isJump() && onGround) {
            velocity.y = JUMP_VELOCITY;
            onGround = false;
        }
    }
    
    /**
     * 更新相机朝向（从服务器接收）
     */
    public void updateCameraOrientation(float deltaYaw, float deltaPitch) {
        this.yaw += deltaYaw;
        this.pitch += deltaPitch;
        this.pitch = Math.max(-90, Math.min(90, this.pitch));
        camera.setYaw(yaw);
        camera.setPitch(pitch);
    }

    /**
     * 从服务器更新事件同步玩家状态（和解逻辑）
     * 当收到服务端的权威位置时，将客户端位置"和解"到服务端位置
     */
    public void updateFromServer(PlayerUpdateEvent event) {
        // 和解位置：服务端的位置是权威的
        // 如果客户端预测的位置与服务端位置差异较大，说明预测有误，需要强制同步
        Vector3d serverPosition = event.getPosition();
        double distance = position.distance(serverPosition);
        
        // 如果差异超过阈值（例如0.5个单位），强制同步到服务端位置
        // 这可以防止客户端预测错误导致的"穿墙"等问题
        if (distance > 0.5f) {
            // 强制同步到服务端位置（橡皮筋效应）
            position.set(serverPosition);
            previousPosition.set(event.getPreviousPosition());
        } else {
            // 平滑插值到服务端位置（减少抖动）
            position.lerp(serverPosition, 0.3f);
            previousPosition.set(event.getPreviousPosition());
        }
        
        // 同步相机朝向（服务端是权威的）
        yaw = event.getYaw();
        pitch = event.getPitch();
        previousYaw = event.getPreviousYaw();
        previousPitch = event.getPreviousPitch();
        camera.setYaw(yaw);
        camera.setPitch(pitch);
        camera.setPreviousYaw(previousYaw);
        camera.setPreviousPitch(previousPitch);
        
        // 同步物品栏选择
        inventory.setSelectedSlot(event.getSelectedSlot());
        
        updateCamera();
    }

    private void updateCamera() {
        Vector3f eyePosition = new Vector3f(position);
        eyePosition.y += eyeHeight;
        camera.setPosition(eyePosition);
        camera.update();
    }

    public float getYaw() {
        return yaw;
    }
    
    public void setYaw(float yaw) {
        this.yaw = yaw;
        camera.setYaw(yaw);
    }
    
    public float getPreviousYaw() {
        return previousYaw;
    }
    
    public float getPitch() {
        return pitch;
    }
    
    public void setPitch(float pitch) {
        this.pitch = Math.max(-90, Math.min(90, pitch));
        camera.setPitch(this.pitch);
    }
    
    public float getPreviousPitch() {
        return previousPitch;
    }
}

