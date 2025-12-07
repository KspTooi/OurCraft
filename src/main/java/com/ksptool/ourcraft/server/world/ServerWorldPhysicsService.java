package com.ksptool.ourcraft.server.world;

import com.ksptool.ourcraft.server.entity.ServerEntity;
import com.ksptool.ourcraft.server.entity.ServerPlayer;
import com.ksptool.ourcraft.server.world.chunk.FlexServerChunkService;
import com.ksptool.ourcraft.sharedcore.BoundingBox;
import com.ksptool.ourcraft.sharedcore.blocks.inner.SharedBlock;
import com.ksptool.ourcraft.sharedcore.utils.position.PrecisionPos;
import com.ksptool.ourcraft.sharedcore.world.BlockState;
import com.ksptool.ourcraft.sharedcore.GlobalPalette;
import com.ksptool.ourcraft.sharedcore.world.WorldService;
import com.ksptool.ourcraft.sharedcore.world.SharedWorld;
import org.joml.Vector3d;

/**
 * 服务端碰撞管理器，负责权威的物理碰撞检测
 * 现在它已经被升级为一个物理服务，负责所有实体/区块的物理计算，并整合到ServerWorld的Action循环中
 */
public class ServerWorldPhysicsService extends WorldService {

    private final ServerWorld world;

    private final SimpleEntityService ses;

    private final FlexServerChunkService fscs;

    private final int chunkSizeX;
    private final int chunkSizeZ;

    public ServerWorldPhysicsService(ServerWorld world) {
        this.world = world;
        this.fscs = world.getFscs();
        this.ses = world.getSes();
        var t = world.getTemplate();
        this.chunkSizeX = t.getChunkSizeX();
        this.chunkSizeZ = t.getChunkSizeZ();
    }

    /**
     * 物理服务动作(实体物理模拟,这会根据实体的速度模拟并更新他们的位置和状态)
     * @param delta 距离上一帧经过的时间（秒）
     * @param world 世界
     */
    @Override
    public void action(double delta, SharedWorld world) {
        
        var entities = ses.getEntities();

        for (ServerEntity item : entities) {

            //处理Player实体物理模拟
            if (item instanceof ServerPlayer pl) {

                //应用玩家输入（移动速度）
                pl.applyInputVelocity();

                //先备份计算前的位置
                var oldPosition = pl.getCurrentChunkPos();

                //如果旧区块位置为空，根据当前位置计算出区块位置（使用高精度Pos避免截断错误）
                if(oldPosition == null){
                    oldPosition = PrecisionPos.of(pl.getPosition()).toChunkPos(chunkSizeX, chunkSizeZ);
                }
                
                pl.setPreviousChunkPos(oldPosition);

                //计算移动后的位置
                pl.update(delta);

                //更新区块位置（使用高精度Pos确保能正确检测跨区块边界）
                pl.setCurrentChunkPos(PrecisionPos.of(pl.getPosition()).toChunkPos(chunkSizeX, chunkSizeZ));
                continue;
            }

            //处理其他实体物理模拟
            item.update(delta);
        }


    }



    public boolean canMoveTo(Vector3d position, double height) {
        int minX = (int) Math.floor(position.x - 0.3f);
        int maxX = (int) Math.floor(position.x + 0.3f);
        int minY = (int) Math.floor(position.y);
        int maxY = (int) Math.floor(position.y + height);
        int minZ = (int) Math.floor(position.z - 0.3f);
        int maxZ = (int) Math.floor(position.z + 0.3f);

        GlobalPalette palette = GlobalPalette.getInstance();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    int stateId = fscs.getBlockStateId(x, y, z);
                    BlockState state = palette.getState(stateId);
                    SharedBlock sharedBlock = state.getSharedBlock();
                    if (sharedBlock.isSolid()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public boolean canMoveTo(BoundingBox box) {
        int minX = (int) Math.floor(box.getMinX());
        int maxX = (int) Math.floor(box.getMaxX());
        int minY = (int) Math.floor(box.getMinY());
        int maxY = (int) Math.floor(box.getMaxY());
        int minZ = (int) Math.floor(box.getMinZ());
        int maxZ = (int) Math.floor(box.getMaxZ());

        GlobalPalette palette = GlobalPalette.getInstance();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    int stateId = fscs.getBlockStateId(x, y, z);
                    BlockState state = palette.getState(stateId);
                    SharedBlock sharedBlock = state.getSharedBlock();
                    if (sharedBlock.isSolid()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }


}

