package com.ksptool.ourcraft.server.world;

import com.ksptool.ourcraft.sharedcore.BoundingBox;
import com.ksptool.ourcraft.sharedcore.blocks.inner.SharedBlock;
import com.ksptool.ourcraft.sharedcore.world.BlockState;
import com.ksptool.ourcraft.sharedcore.world.GlobalPalette;
import org.joml.Vector3f;

/**
 * 服务端碰撞管理器，负责权威的物理碰撞检测
 */
public class ServerCollisionManager {
    private final ServerWorld world;
    
    public ServerCollisionManager(ServerWorld world) {
        this.world = world;
    }

    public boolean canMoveTo(Vector3f position, float height) {
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
                    int stateId = world.getChunkManager().getBlockState(x, y, z);
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
                    int stateId = world.getChunkManager().getBlockState(x, y, z);
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

