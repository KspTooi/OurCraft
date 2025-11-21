package com.ksptool.ourcraft.client.world;

import com.ksptool.ourcraft.sharedcore.blocks.inner.SharedBlock;
import com.ksptool.ourcraft.sharedcore.world.BlockState;
import com.ksptool.ourcraft.sharedcore.world.GlobalPalette;
import com.ksptool.ourcraft.sharedcore.world.RaycastResult;
import org.joml.Vector3f;

/**
 * 客户端射线检测类，用于瞄准框渲染（非权威）
 * 注意：此类的计算结果仅用于视觉反馈，实际的方块交互由服务端权威决定
 */
public class ClientRaycast {
    public static RaycastResult cast(ClientWorld world, Vector3f origin, Vector3f direction, float maxDistance) {
        RaycastResult result = new RaycastResult();

        Vector3f rayDir = new Vector3f(direction).normalize();
        Vector3f rayPos = new Vector3f(origin);

        float stepSize = 0.1f;
        float distance = 0.0f;

        while (distance < maxDistance) {
            int blockX = (int) Math.floor(rayPos.x);
            int blockY = (int) Math.floor(rayPos.y);
            int blockZ = (int) Math.floor(rayPos.z);

            int stateId = world.getBlockState(blockX, blockY, blockZ);
            if (stateId == 0) {
                // 如果区块未加载，继续前进（预测）
                rayPos.add(rayDir.x * stepSize, rayDir.y * stepSize, rayDir.z * stepSize);
                distance += stepSize;
                continue;
            }
            
            GlobalPalette palette = GlobalPalette.getInstance();
            BlockState state = palette.getState(stateId);
            SharedBlock sharedBlock = state.getSharedBlock();
            if (sharedBlock.isSolid()) {
                result.setHit(true);
                result.getBlockPosition().set(blockX, blockY, blockZ);

                int prevX = (int) Math.floor(rayPos.x - rayDir.x * stepSize);
                int prevY = (int) Math.floor(rayPos.y - rayDir.y * stepSize);
                int prevZ = (int) Math.floor(rayPos.z - rayDir.z * stepSize);

                if (prevX != blockX) {
                    result.getFaceNormal().set(prevX < blockX ? -1 : 1, 0, 0);
                } else if (prevY != blockY) {
                    result.getFaceNormal().set(0, prevY < blockY ? -1 : 1, 0);
                } else if (prevZ != blockZ) {
                    result.getFaceNormal().set(0, 0, prevZ < blockZ ? -1 : 1);
                }

                return result;
            }

            rayPos.add(rayDir.x * stepSize, rayDir.y * stepSize, rayDir.z * stepSize);
            distance += stepSize;
        }

        return result;
    }
}

