package com.ksptool.mycraft.world.gen.layers;

import com.ksptool.mycraft.world.Block;
import com.ksptool.mycraft.world.Chunk;
import com.ksptool.mycraft.world.gen.GenerationContext;
import com.ksptool.mycraft.world.gen.ITerrainLayer;

/**
 * 基础密度层，生成世界的3D基础形状（石头和空气）
 */
public class BaseDensityLayer implements ITerrainLayer {
    private static final int AIR_STATE_ID = 0;

    @Override
    public void apply(int[][][] chunkData, int chunkX, int chunkZ, GenerationContext context) {
        Block stoneBlock = context.getRegistry().get("mycraft:stone");
        if (stoneBlock == null) {
            return;
        }
        
        int stoneStateId = context.getGlobalPalette().getStateId(stoneBlock.getDefaultState());

        for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
            for (int y = 0; y < Chunk.CHUNK_HEIGHT; y++) {
                for (int z = 0; z < Chunk.CHUNK_SIZE; z++) {
                    int worldX = chunkX * Chunk.CHUNK_SIZE + x;
                    int worldY = y;
                    int worldZ = chunkZ * Chunk.CHUNK_SIZE + z;

                    double noise = context.getNoiseGenerator().getNoise(worldX * 0.01, worldY * 0.01, worldZ * 0.01);
                    double density = -worldY + 64 + noise * 20;

                    if (density > 0) {
                        chunkData[x][y][z] = stoneStateId;
                    } else {
                        chunkData[x][y][z] = AIR_STATE_ID;
                    }
                }
            }
        }
    }
}

