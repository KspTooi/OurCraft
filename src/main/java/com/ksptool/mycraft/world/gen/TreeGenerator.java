package com.ksptool.mycraft.world.gen;

import com.ksptool.mycraft.world.Block;
import com.ksptool.mycraft.world.BlockState;
import com.ksptool.mycraft.world.Chunk;
import com.ksptool.mycraft.world.GlobalPalette;
import com.ksptool.mycraft.world.Registry;
import com.ksptool.mycraft.world.blocks.WoodBlock;

/**
 * 树木生成器类，负责生成树木结构
 */
public class TreeGenerator {
    private static final int TREE_HEIGHT_MIN = 4;
    private static final int TREE_HEIGHT_MAX = 7;
    private static final int LEAF_RADIUS = 2;

    public static void place(int[][][] chunkData, int x, int y, int z, GenerationContext context) {
        if (x < 0 || x >= Chunk.CHUNK_SIZE || z < 0 || z >= Chunk.CHUNK_SIZE) {
            return;
        }
        if (y < 0 || y >= Chunk.CHUNK_HEIGHT) {
            return;
        }

        GlobalPalette palette = context.getGlobalPalette();
        Registry registry = context.getRegistry();

        Block woodBlock = registry.get("mycraft:wood");
        Block leavesBlock = registry.get("mycraft:leaves");

        if (woodBlock == null || leavesBlock == null) {
            return;
        }

        BlockState woodStateY = woodBlock.getDefaultState();
        if (woodBlock instanceof WoodBlock) {
            woodStateY = woodStateY.with(WoodBlock.AXIS, WoodBlock.Axis.Y);
        }
        BlockState leavesState = leavesBlock.getDefaultState();

        int woodStateId = palette.getStateId(woodStateY);
        int leavesStateId = palette.getStateId(leavesState);

        int treeHeight = TREE_HEIGHT_MIN + (int) (Math.random() * (TREE_HEIGHT_MAX - TREE_HEIGHT_MIN + 1));

        if (y + treeHeight >= Chunk.CHUNK_HEIGHT) {
            return;
        }

        for (int dy = 0; dy < treeHeight; dy++) {
            int currentY = y + dy;
            if (currentY >= 0 && currentY < Chunk.CHUNK_HEIGHT) {
                chunkData[x][currentY][z] = woodStateId;
            }
        }

        int leafStartY = y + treeHeight - 2;
        int leafEndY = y + treeHeight;

        for (int leafY = leafStartY; leafY <= leafEndY; leafY++) {
            if (leafY < 0 || leafY >= Chunk.CHUNK_HEIGHT) {
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

                    if (leafX < 0 || leafX >= Chunk.CHUNK_SIZE || leafZ < 0 || leafZ >= Chunk.CHUNK_SIZE) {
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

