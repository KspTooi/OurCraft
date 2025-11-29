package com.ksptool.ourcraft.server.entity;

import com.ksptool.ourcraft.server.archive.model.ArchivePlayerVo;
import com.ksptool.ourcraft.server.item.ServerInventory;
import com.ksptool.ourcraft.sharedcore.BoundingBox;
import com.ksptool.ourcraft.sharedcore.blocks.inner.SharedBlock;
import com.ksptool.ourcraft.sharedcore.enums.BlockEnums;
import com.ksptool.ourcraft.sharedcore.events.PlayerInputEvent;
import com.ksptool.ourcraft.sharedcore.GlobalPalette;
import com.ksptool.ourcraft.sharedcore.Registry;
import com.ksptool.ourcraft.server.world.ServerRaycast;
import com.ksptool.ourcraft.sharedcore.world.RaycastResult;
import com.ksptool.ourcraft.server.world.ServerWorld;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector3i;

import java.util.UUID;

/**
 * 服务端玩家实体类，处理玩家移动、方块放置和破坏
 */
@Getter
public class ServerPlayer extends ServerLivingEntity {

    //玩家名称
    private final String name;

    //背包
    private final ServerInventory inventory;

    //相机朝向（服务端存储，用于同步给客户端）
    @Setter
    private double yaw = 0.0;

    @Setter
    private double pitch = 0.0;

    //地面加速度
    private static final float GROUND_ACCELERATION = 40F;
    
    //空中加速度
    private static final float AIR_ACCELERATION = 5F;
    
    //最大移动速度
    private static final float MAX_SPEED = 40F;

    /**
     * 服务端构造函数：创建一个与服务端世界关联的玩家对象（带UUID）
     */
    public ServerPlayer(ServerWorld world, ArchivePlayerVo vo) {
        super(world, vo != null && vo.getUuid() != null ? UUID.fromString(vo.getUuid()) : UUID.randomUUID());

        if(vo == null){
            throw new IllegalArgumentException("玩家数据不能为空");
        }
        if(world == null){
            throw new IllegalArgumentException("世界不能为空");
        }

        this.inventory = new ServerInventory();
        this.eyeHeight = 1.6f;
        this.boundingBox = new BoundingBox(position, 0.6f, 1.8f);
        
        if(vo != null && vo.getName() != null){
            this.name = vo.getName();
        } else {
            this.name = "Unknown";
        }
        
        if(vo.getPosX() != null && vo.getPosY() != null && vo.getPosZ() != null){
            this.position.set((float)vo.getPosX().doubleValue(), (float)vo.getPosY().doubleValue(), (float)vo.getPosZ().doubleValue());
        }
        if(vo.getYaw() != null){
            this.yaw = vo.getYaw();
        }
        if(vo.getPitch() != null){
            this.pitch = vo.getPitch();
        }
        if(vo.getHealth() != null){
            setHealth(vo.getHealth().floatValue());
        }
        if(vo.getHungry() != null){
            setHunger(vo.getHungry().floatValue());
        }
    }

    @Override
    public void update(float delta) {
        super.update(delta);
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
            float tickDelta = 1.0f / world.getTemplate().getTps();
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
        this.pitch = Math.max(-90.0, Math.min(90.0, this.pitch));
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
            SharedBlock airSharedBlock = registry.getBlock(BlockEnums.AIR.getStdRegName());
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
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        return new Vector3f(
                (float) (Math.sin(yawRad) * Math.cos(pitchRad)),
                (float) (-Math.sin(pitchRad)),
                (float) (-Math.cos(yawRad) * Math.cos(pitchRad))
        );
    }

}

