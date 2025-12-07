package com.ksptool.ourcraft.server.world.gen.layers;

import com.ksptool.ourcraft.sharedcore.enums.BlockEnums;
import com.ksptool.ourcraft.server.world.ServerWorld;
import com.ksptool.ourcraft.sharedcore.blocks.inner.SharedBlock;
import com.ksptool.ourcraft.sharedcore.world.gen.GenerationContext;
import com.ksptool.ourcraft.sharedcore.world.gen.TerrainLayer;

/**
 * 水体层，在特定高度填充水
 */
public class WaterLayer implements TerrainLayer {
    private static final int AIR_STATE_ID = 0;
    private static final int SEA_LEVEL = 63;

    @Override
    public void apply(int[][][] chunkData, int chunkX, int chunkZ, GenerationContext context) {
        SharedBlock waterSharedBlock = context.getRegistry().getBlock(BlockEnums.WATER.getStdRegName());
        if (waterSharedBlock == null) {
            return;
        }

        var t = ((ServerWorld)context.getWorld()).getTemplate();
        var chunkSizeX = t.getChunkSizeX();
        var chunkSizeZ = t.getChunkSizeZ();
        int waterStateId = context.getGlobalPalette().getStateId(waterSharedBlock.getDefaultState());

        for (int x = 0; x < chunkSizeX; x++) {
            for (int y = 0; y < SEA_LEVEL; y++) {
                for (int z = 0; z < chunkSizeZ; z++) {
                    if (chunkData[x][y][z] == AIR_STATE_ID) {
                        chunkData[x][y][z] = waterStateId;
                    }
                }
            }
        }
    }
}

