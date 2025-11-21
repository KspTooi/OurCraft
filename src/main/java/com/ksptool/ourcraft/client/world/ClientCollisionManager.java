package com.ksptool.ourcraft.client.world;

import com.ksptool.ourcraft.sharedcore.BoundingBox;
import com.ksptool.ourcraft.sharedcore.blocks.inner.SharedBlock;
import com.ksptool.ourcraft.sharedcore.world.BlockState;
import com.ksptool.ourcraft.sharedcore.world.GlobalPalette;
import org.joml.Vector3f;

/**
 * 客户端碰撞管理器，用于客户端移动预测（非权威）
 * 注意：此类的计算结果仅用于本地预测，最终位置由服务端权威决定
 */
public class ClientCollisionManager {
    private final ClientWorld world;
    
    public ClientCollisionManager(ClientWorld world) {
        this.world = world;
    }

    /**
     * 预测移动是否可行（非权威，仅用于本地预测）
     */
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
                    int stateId = world.getBlockState(x, y, z);
                    if (stateId == 0) {
                        continue; // 如果区块未加载，假设可以移动（预测）
                    }
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

    /**
     * 预测边界框移动是否可行（非权威，仅用于本地预测）
     */
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
                    int stateId = world.getBlockState(x, y, z);
                    if (stateId == 0) {
                        continue; // 如果区块未加载，假设可以移动（预测）
                    }
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

