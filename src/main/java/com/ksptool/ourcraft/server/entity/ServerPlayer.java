package com.ksptool.ourcraft.server.entity;

import com.ksptool.ourcraft.server.archive.model.ArchivePlayerVo;
import com.ksptool.ourcraft.server.item.ServerInventory;
import com.ksptool.ourcraft.server.network.NetworkSession;
import com.ksptool.ourcraft.sharedcore.BoundingBox;
import com.ksptool.ourcraft.sharedcore.blocks.inner.SharedBlock;
import com.ksptool.ourcraft.sharedcore.enums.BlockEnums;
import com.ksptool.ourcraft.server.event.ServerPlayerInputEvent;
import com.ksptool.ourcraft.sharedcore.events.PlayerInputEvent;
import com.ksptool.ourcraft.sharedcore.GlobalPalette;
import com.ksptool.ourcraft.sharedcore.Registry;
import com.ksptool.ourcraft.server.world.ServerRaycast;
import com.ksptool.ourcraft.sharedcore.utils.position.ChunkPos;
import com.ksptool.ourcraft.sharedcore.utils.position.Pos;
import com.ksptool.ourcraft.sharedcore.world.RaycastResult;
import com.ksptool.ourcraft.server.world.ServerWorld;
import lombok.Getter;
import lombok.Setter;
import org.joml.*;

import java.lang.Math;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 服务端玩家实体类，处理玩家移动、方块放置和破坏
 */
@Getter
public class ServerPlayer extends ServerLivingEntity {

    //玩家网络会话
    private final NetworkSession session;

    //玩家名称
    private final String name;

    //背包
    private final ServerInventory inventory;

    //相机朝向（服务端存储，用于同步给客户端）
    @Setter
    private double yaw = 0.0;

    @Setter
    private double pitch = 0.0;

    //本次Action结束时的区块地址
    @Setter
    private ChunkPos currentChunkPos;

    //上一个Action结束时的区块地址
    @Setter
    private ChunkPos previousChunkPos;

    //地面加速度
    private static final float GROUND_ACCELERATION = 40F;
    
    //空中加速度
    private static final float AIR_ACCELERATION = 5F;
    
    //最大移动速度
    private static final float MAX_SPEED = 40F;

    //视口距离
    private int viewDistance = 4;

    //租约是否初始化过(当Player第一次加入这个世界时,他并没有跨过区块边界,因此无法通过跨区块检测来触发初始租约签发,需要特殊处理来初始化其视口范围内的区块租约)
    private AtomicBoolean isLeaseInited = new AtomicBoolean(false);


    /**
     * 服务端构造函数：创建一个与服务端世界关联的Player对象（带UUID）
     */
    public ServerPlayer(ServerWorld world, ArchivePlayerVo vo, NetworkSession session) {

        super(world, vo != null && vo.getUuid() != null ? UUID.fromString(vo.getUuid()) : UUID.randomUUID());
        this.session = session;

        if(vo == null){
            throw new IllegalArgumentException("玩家数据不能为空");
        }

        this.inventory = new ServerInventory();
        this.eyeHeight = 1.6f;
        this.boundingBox = new BoundingBox(position, 0.6f, 1.8f);
        
        if(vo.getName() != null){
            this.name = vo.getName();
        } else {
            this.name = "Unknown";
        }
        
        if(vo.getPosX() != null && vo.getPosY() != null && vo.getPosZ() != null){
            this.position.set((float)vo.getPosX().doubleValue(), (float)vo.getPosY().doubleValue(), (float)vo.getPosZ().doubleValue());
            // [新增] 同步更新包围盒位置，修复错位 Bug
            this.boundingBox.update(this.position);
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
        //初始化当前区块位置(使用落地位置)
        var groundPos = vo.getGroundPos();
        var chunkPos = groundPos.toChunkPos(world.getTemplate().getChunkSizeX(), world.getTemplate().getChunkSizeZ());
        currentChunkPos = chunkPos;
        previousChunkPos = chunkPos;
    }

    @Override
    public void update(double delta) {
        // 在更新物理位置之前，先保存当前的区块位置作为"上一次"的位置
        // 或者，如果你想比较的是"上一帧的最终位置"和"这一帧的最终位置"，
        // 那么应该在super.update之前记录。

        // 获取当前位置对应的区块（更新前）
        //ChunkPos currentChunk = Pos.of(position.x, position.y, position.z).toChunkPos(world.getTemplate().getChunkSizeX(), world.getTemplate().getChunkSizeZ());

        // 如果 previousActionChunkPos 还没初始化，先初始化它
        //if (previousActionChunkPos == null) {
        //    previousActionChunkPos = currentChunk;
        //}

        super.update(delta);
        
        // 注意：这里我们仅仅是执行物理更新。
        // 实际上，票据更新逻辑（Token update）通常是在 ServerWorld.action 中
        // 通过比较 player.getPreviousActionChunkPos() 和 player.getCurrentChunkPos() 来触发的。
        // 触发完票据更新后，ServerWorld 会负责将 previousActionChunkPos 更新为当前位置。
        
        // 但如果你希望 ServerPlayer 自动维护这个字段，
        // 你可以定义一个方法供外部调用，或者在这里不更新，
        // 而是提供一个 setPreviousActionChunkPos 方法给 World 使用。
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
            float tickDelta = 1.0f / world.getTemplate().getActionPerSecond();
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
     * 应用玩家输入（适配ServerPlayerInputEvent）
     */
    public void applyInput(ServerPlayerInputEvent event) {
        if (event == null) {
            return;
        }
        PlayerInputEvent inputEvent = new PlayerInputEvent(
            event.isW(),
            event.isS(),
            event.isA(),
            event.isD(),
            event.isSpace()
        );
        applyInput(inputEvent);
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
            if (world.canMoveTo(new Vector3d(placePos.x, placePos.y, placePos.z), boundingBox.getHeight())) {
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

    public boolean isLeaseInited() {
        return isLeaseInited.get();
    }

    public void markLeaseInited() {
        isLeaseInited.set(true);
    }

}

