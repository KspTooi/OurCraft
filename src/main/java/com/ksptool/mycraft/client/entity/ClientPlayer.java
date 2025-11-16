package com.ksptool.mycraft.client.entity;

import com.ksptool.mycraft.client.Input;
import com.ksptool.mycraft.item.Inventory;
import com.ksptool.mycraft.sharedcore.events.PlayerUpdateEvent;
import lombok.Getter;
import org.joml.Vector2d;
import org.joml.Vector3f;

/**
 * 客户端玩家实体类，负责渲染和输入处理
 */
@Getter
public class ClientPlayer extends ClientEntity {
    //相机
    private final Camera camera;

    //背包
    private final Inventory inventory;

    //鼠标灵敏度
    private float mouseSensitivity = 0.1f;
    
    //眼睛高度
    private float eyeHeight = 1.6f;
    
    //生命值（从服务器同步）
    private float health = 40.0f;
    private float maxHealth = 40.0f;
    
    //饥饿值（从服务器同步）
    private float hunger = 40.0f;
    private float maxHunger = 40.0f;

    public ClientPlayer() {
        super(java.util.UUID.randomUUID());
        this.camera = new Camera();
        this.inventory = new Inventory();
    }

    public ClientPlayer(java.util.UUID uniqueId) {
        super(uniqueId);
        this.camera = new Camera();
        this.inventory = new Inventory();
    }

    public void initializeCamera() {
        camera.setYaw(0);
        camera.setPitch(0);
        updateCamera();
    }

    public void update(float delta) {
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

            camera.setYaw(camera.getYaw() + deltaYaw);
            camera.setPitch(camera.getPitch() + deltaPitch);
        }
    }

    /**
     * 从服务器更新事件同步玩家状态
     */
    public void updateFromServer(PlayerUpdateEvent event) {
        position.set(event.getPosition());
        previousPosition.set(event.getPreviousPosition());
        camera.setYaw(event.getYaw());
        camera.setPitch(event.getPitch());
        camera.setPreviousYaw(event.getPreviousYaw());
        camera.setPreviousPitch(event.getPreviousPitch());
        inventory.setSelectedSlot(event.getSelectedSlot());
        
        updateCamera();
    }

    private void updateCamera() {
        Vector3f eyePosition = new Vector3f(position);
        eyePosition.y += eyeHeight;
        camera.setPosition(eyePosition);
        camera.update();
    }

    public float getEyeHeight() {
        return eyeHeight;
    }
    
    public float getHealth() {
        return health;
    }
    
    public float getMaxHealth() {
        return maxHealth;
    }
    
    public float getHunger() {
        return hunger;
    }
    
    public float getMaxHunger() {
        return maxHunger;
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

