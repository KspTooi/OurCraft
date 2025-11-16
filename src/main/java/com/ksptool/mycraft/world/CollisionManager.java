package com.ksptool.mycraft.world;

import com.ksptool.mycraft.server.world.ServerWorld;
import com.ksptool.mycraft.sharedcore.BoundingBox;
import com.ksptool.mycraft.sharedcore.world.BlockState;
import org.joml.Vector3f;

/**
 * 碰撞管理器，负责物理碰撞检测
 */
public class CollisionManager {
    private final ServerWorld world;
    
    public CollisionManager(ServerWorld world) {
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
                    Block block = state.getBlock();
                    if (block.isSolid()) {
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
                    Block block = state.getBlock();
                    if (block.isSolid()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
