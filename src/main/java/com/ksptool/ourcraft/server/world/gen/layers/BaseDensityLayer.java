package com.ksptool.ourcraft.server.world.gen.layers;

import com.ksptool.ourcraft.server.world.chunk.ServerChunkOld;
import com.ksptool.ourcraft.sharedcore.enums.BlockEnums;
import com.ksptool.ourcraft.sharedcore.blocks.inner.SharedBlock;
import com.ksptool.ourcraft.sharedcore.world.gen.GenerationContext;
import com.ksptool.ourcraft.sharedcore.world.gen.TerrainLayer;

/**
 * 基础密度层，生成世界的3D基础形状（石头和空气）
 */
public class BaseDensityLayer implements TerrainLayer {

    //空气状态ID
    private static final int AIR_STATE_ID = 0;

    @Override
    public void apply(int[][][] chunkData, int chunkX, int chunkZ, GenerationContext context) {
        SharedBlock stoneSharedBlock = context.getRegistry().getBlock(BlockEnums.STONE.getStdRegName());
        if (stoneSharedBlock == null) {
            return;
        }
        
        int stoneStateId = context.getGlobalPalette().getStateId(stoneSharedBlock.getDefaultState());

        for (int x = 0; x < ServerChunkOld.CHUNK_SIZE; x++) {
            for (int y = 0; y < ServerChunkOld.CHUNK_HEIGHT; y++) {
                for (int z = 0; z < ServerChunkOld.CHUNK_SIZE; z++) {
                    int worldX = chunkX * ServerChunkOld.CHUNK_SIZE + x;
                    int worldY = y;
                    int worldZ = chunkZ * ServerChunkOld.CHUNK_SIZE + z;

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

