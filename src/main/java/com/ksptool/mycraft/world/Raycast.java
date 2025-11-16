package com.ksptool.mycraft.world;

import com.ksptool.mycraft.server.world.ServerWorld;
import com.ksptool.mycraft.sharedcore.world.BlockState;
import org.joml.Vector3f;

/**
 * 射线检测类，用于检测射线与方块的碰撞
 */
public class Raycast {
    public static RaycastResult cast(ServerWorld world, Vector3f origin, Vector3f direction, float maxDistance) {
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
            GlobalPalette palette = GlobalPalette.getInstance();
            BlockState state = palette.getState(stateId);
            Block block = state.getBlock();
            if (block.isSolid()) {
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

