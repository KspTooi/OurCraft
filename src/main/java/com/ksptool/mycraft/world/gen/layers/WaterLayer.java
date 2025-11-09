package com.ksptool.mycraft.world.gen.layers;

import com.ksptool.mycraft.world.Block;
import com.ksptool.mycraft.world.Chunk;
import com.ksptool.mycraft.world.gen.GenerationContext;
import com.ksptool.mycraft.world.gen.ITerrainLayer;

/**
 * 水体层，在特定高度填充水
 */
public class WaterLayer implements ITerrainLayer {
    private static final int AIR_STATE_ID = 0;
    private static final int SEA_LEVEL = 63;

    @Override
    public void apply(int[][][] chunkData, int chunkX, int chunkZ, GenerationContext context) {
        Block waterBlock = context.getRegistry().get("mycraft:water");
        if (waterBlock == null) {
            return;
        }
        
        int waterStateId = context.getGlobalPalette().getStateId(waterBlock.getDefaultState());

        for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
            for (int y = 0; y < SEA_LEVEL; y++) {
                for (int z = 0; z < Chunk.CHUNK_SIZE; z++) {
                    if (chunkData[x][y][z] == AIR_STATE_ID) {
                        chunkData[x][y][z] = waterStateId;
                    }
                }
            }
        }
    }
}

