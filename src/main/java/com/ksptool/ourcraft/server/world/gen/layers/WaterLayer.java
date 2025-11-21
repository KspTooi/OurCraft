package com.ksptool.ourcraft.server.world.gen.layers;

import com.ksptool.ourcraft.sharedcore.BlockType;
import com.ksptool.ourcraft.sharedcore.blocks.inner.SharedBlock;
import com.ksptool.ourcraft.server.world.ServerChunk;
import com.ksptool.ourcraft.server.world.gen.GenerationContext;
import com.ksptool.ourcraft.server.world.gen.ITerrainLayer;

/**
 * 水体层，在特定高度填充水
 */
public class WaterLayer implements ITerrainLayer {
    private static final int AIR_STATE_ID = 0;
    private static final int SEA_LEVEL = 63;

    @Override
    public void apply(int[][][] chunkData, int chunkX, int chunkZ, GenerationContext context) {
        SharedBlock waterSharedBlock = context.getRegistry().getBlock(BlockType.WATER.getNamespacedId());
        if (waterSharedBlock == null) {
            return;
        }
        
        int waterStateId = context.getGlobalPalette().getStateId(waterSharedBlock.getDefaultState());

        for (int x = 0; x < ServerChunk.CHUNK_SIZE; x++) {
            for (int y = 0; y < SEA_LEVEL; y++) {
                for (int z = 0; z < ServerChunk.CHUNK_SIZE; z++) {
                    if (chunkData[x][y][z] == AIR_STATE_ID) {
                        chunkData[x][y][z] = waterStateId;
                    }
                }
            }
        }
    }
}

