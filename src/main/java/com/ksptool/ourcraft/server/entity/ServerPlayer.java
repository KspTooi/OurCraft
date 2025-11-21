package com.ksptool.ourcraft.server.entity;

import com.ksptool.ourcraft.server.item.ServerInventory;
import com.ksptool.ourcraft.sharedcore.BoundingBox;
import com.ksptool.ourcraft.sharedcore.blocks.inner.SharedBlock;
import com.ksptool.ourcraft.sharedcore.events.PlayerInputEvent;
import com.ksptool.ourcraft.sharedcore.BlockType;
import com.ksptool.ourcraft.sharedcore.world.GlobalPalette;
import com.ksptool.ourcraft.sharedcore.world.Registry;
import com.ksptool.ourcraft.server.world.ServerRaycast;
import com.ksptool.ourcraft.sharedcore.world.RaycastResult;
import com.ksptool.ourcraft.server.world.ServerWorld;
import com.ksptool.ourcraft.server.world.save.ItemStackData;
import com.ksptool.ourcraft.server.world.save.PlayerIndex;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector3i;

/**
 * 服务端玩家实体类，处理玩家移动、方块放置和破坏
 */
@Getter
public class ServerPlayer extends ServerLivingEntity {
    //背包
    private final ServerInventory inventory;

    //相机朝向（服务端存储，用于同步给客户端）
    @Setter
    private float yaw = 0.0f;
    @Setter
    private float pitch = 0.0f;
    @Setter
    private float previousYaw = 0.0f;
    @Setter
    private float previousPitch = 0.0f;
    
    //地面加速度
    private static final float GROUND_ACCELERATION = 40F;
    
    //空中加速度
    private static final float AIR_ACCELERATION = 5F;
    
    //最大移动速度
    private static final float MAX_SPEED = 40F;

    /**
     * 服务端构造函数：创建一个与服务端世界关联的玩家对象
     */
    public ServerPlayer(ServerWorld world) {
        super(world);
        this.inventory = new ServerInventory();
        this.eyeHeight = 1.6f;
        this.boundingBox = new BoundingBox(position, 0.6f, 1.8f);
    }

    /**
     * 服务端构造函数：创建一个与服务端世界关联的玩家对象（带UUID）
     */
    public ServerPlayer(ServerWorld world, java.util.UUID uniqueId) {
        super(world, uniqueId);
        this.inventory = new ServerInventory();
        this.eyeHeight = 1.6f;
        this.boundingBox = new BoundingBox(position, 0.6f, 1.8f);
    }

    @Override
    public void update(float delta) {
        super.update(delta);
        // 更新previousYaw和previousPitch用于插值
        previousYaw = yaw;
        previousPitch = pitch;
    }
    
    /**
     * 应用玩家输入（移动方向）
     * 注意：这个方法现在接收的是已经处理过的移动方向，而不是原始输入事件
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
            float tickDelta = 1.0f / world.getTemplate().getTicksPerSecond();
            velocity.x += moveDirection.x * acceleration * tickDelta;
            velocity.z += moveDirection.z * acceleration * tickDelta;
            
            Vector2f horizontalVelocity = new Vector2f(velocity.x, velocity.z);
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
     * 更新相机朝向（从客户端接收）
     */
    public void updateCameraOrientation(float deltaYaw, float deltaPitch) {
        this.yaw += deltaYaw;
        this.pitch += deltaPitch;
        // 限制pitch范围
        this.pitch = Math.max(-90, Math.min(90, this.pitch));
        markDirty(true);
    }

    public void handleBlockBreak() {
        Vector3f eyePosition = new Vector3f(position);
        eyePosition.y += eyeHeight;
        Vector3f direction = getLookDirection();
        RaycastResult result = ServerRaycast.cast(world, eyePosition, direction, 5.0f);
        if (result.isHit()) {
            GlobalPalette palette = GlobalPalette.getInstance();
            Registry registry = Registry.getInstance();
            SharedBlock airSharedBlock = registry.getBlock(BlockType.AIR.getStdRegName());
            int airStateId = palette.getStateId(airSharedBlock.getDefaultState());
            world.setBlockState(result.getBlockPosition().x, result.getBlockPosition().y, result.getBlockPosition().z, airStateId);
        }
    }

    public void handleBlockPlace() {
        com.ksptool.ourcraft.sharedcore.item.ItemStack selectedStack = inventory.getSelectedItem();
        if (selectedStack == null || selectedStack.isEmpty()) {
            return;
        }

        Vector3f eyePosition = new Vector3f(position);
        eyePosition.y += eyeHeight;
        Vector3f direction = getLookDirection();
        RaycastResult result = ServerRaycast.cast(world, eyePosition, direction, 5.0f);
        if (result.isHit()) {
            Vector3i placePos = new Vector3i(result.getBlockPosition()).add(result.getFaceNormal());
            if (world.canMoveTo(new Vector3f(placePos.x, placePos.y, placePos.z), boundingBox.getHeight())) {
                String blockId = selectedStack.getItem().getBlockNamespacedID();
                if (blockId != null) {
                    GlobalPalette palette = GlobalPalette.getInstance();
                    Registry registry = Registry.getInstance();
                    SharedBlock sharedBlock = registry.getBlock(blockId);
                    if (sharedBlock != null) {
                        int stateId = palette.getStateId(sharedBlock.getDefaultState());
                        world.setBlockState(placePos.x, placePos.y, placePos.z, stateId);
                        selectedStack.remove(1);
                        markDirty(true);
                    }
                }
            }
        }
    }

    private Vector3f getLookDirection() {
        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);
        return new Vector3f(
                (float) (Math.sin(yawRad) * Math.cos(pitchRad)),
                (float) (-Math.sin(pitchRad)),
                (float) (-Math.cos(yawRad) * Math.cos(pitchRad))
        );
    }

    public void loadFromPlayerIndex(PlayerIndex playerIndex) {
        if (playerIndex == null) {
            return;
        }

        position.set(playerIndex.posX, playerIndex.posY, playerIndex.posZ);
        this.yaw = playerIndex.yaw;
        this.pitch = playerIndex.pitch;
        this.previousYaw = playerIndex.yaw;
        this.previousPitch = playerIndex.pitch;
        setHealth(playerIndex.health);
        inventory.setSelectedSlot(playerIndex.selectedSlot);

        if (playerIndex.hotbar != null) {
            com.ksptool.ourcraft.sharedcore.item.ItemStack[] hotbar = inventory.getHotbar();
            for (int i = 0; i < Math.min(playerIndex.hotbar.size(), hotbar.length); i++) {
                ItemStackData stackData = playerIndex.hotbar.get(i);
                if (stackData != null && stackData.itemId != null && stackData.count != null) {
                    com.ksptool.ourcraft.sharedcore.item.Item item = com.ksptool.ourcraft.sharedcore.item.Item.getItem(stackData.itemId);
                    if (item != null) {
                        hotbar[i] = new com.ksptool.ourcraft.sharedcore.item.ItemStack(item, stackData.count);
                    }
                } else {
                    hotbar[i] = null;
                }
            }
        }
    }
}

