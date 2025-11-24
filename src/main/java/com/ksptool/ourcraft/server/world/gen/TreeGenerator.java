package com.ksptool.ourcraft.server.world.gen;

import com.ksptool.ourcraft.sharedcore.Registry;
import com.ksptool.ourcraft.sharedcore.enums.BlockEnums;
import com.ksptool.ourcraft.sharedcore.GlobalPalette;
import com.ksptool.ourcraft.sharedcore.blocks.inner.SharedBlock;
import com.ksptool.ourcraft.sharedcore.blocks.WoodBlock;
import com.ksptool.ourcraft.sharedcore.world.BlockState;
import com.ksptool.ourcraft.server.world.chunk.ServerChunk;

/**
 * 树木生成器类，负责生成树木结构
 */
public class TreeGenerator {

    //树木高度最小值
    private static final int TREE_HEIGHT_MIN = 4;

    //树木高度最大值
    private static final int TREE_HEIGHT_MAX = 7;
    
    //树叶半径
    private static final int LEAF_RADIUS = 2;

    public static void place(int[][][] chunkData, int x, int y, int z, GenerationContext context) {
        if (x < 0 || x >= ServerChunk.CHUNK_SIZE || z < 0 || z >= ServerChunk.CHUNK_SIZE) {
            return;
        }
        if (y < 0 || y >= ServerChunk.CHUNK_HEIGHT) {
            return;
        }

        GlobalPalette palette = context.getGlobalPalette();
        Registry registry = context.getRegistry();

        SharedBlock woodSharedBlock = registry.getBlock(BlockEnums.WOOD.getStdRegName());
        SharedBlock leavesSharedBlock = registry.getBlock(BlockEnums.LEAVES.getStdRegName());

        if (woodSharedBlock == null || leavesSharedBlock == null) {
            return;
        }

        BlockState woodStateY = woodSharedBlock.getDefaultState();
        if (woodSharedBlock instanceof WoodBlock) {
            woodStateY = woodStateY.with(WoodBlock.AXIS, WoodBlock.Axis.Y);
        }
        BlockState leavesState = leavesSharedBlock.getDefaultState();

        int woodStateId = palette.getStateId(woodStateY);
        int leavesStateId = palette.getStateId(leavesState);

        int treeHeight = TREE_HEIGHT_MIN + (int) (Math.random() * (TREE_HEIGHT_MAX - TREE_HEIGHT_MIN + 1));

        if (y + treeHeight >= ServerChunk.CHUNK_HEIGHT) {
            return;
        }

        for (int dy = 0; dy < treeHeight; dy++) {
            int currentY = y + dy;
            if (currentY >= 0 && currentY < ServerChunk.CHUNK_HEIGHT) {
                chunkData[x][currentY][z] = woodStateId;
            }
        }

        int leafStartY = y + treeHeight - 2;
        int leafEndY = y + treeHeight;

        for (int leafY = leafStartY; leafY <= leafEndY; leafY++) {
            if (leafY < 0 || leafY >= ServerChunk.CHUNK_HEIGHT) {
                continue;
            }

            int radius = leafY == leafEndY ? LEAF_RADIUS - 1 : LEAF_RADIUS;

            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz > radius * radius) {
                        continue;
                    }

                    int leafX = x + dx;
                    int leafZ = z + dz;

                    if (leafX < 0 || leafX >= ServerChunk.CHUNK_SIZE || leafZ < 0 || leafZ >= ServerChunk.CHUNK_SIZE) {
                        continue;
                    }

                    if (chunkData[leafX][leafY][leafZ] == 0) {
                        chunkData[leafX][leafY][leafZ] = leavesStateId;
                    }
                }
            }
        }
    }
}

