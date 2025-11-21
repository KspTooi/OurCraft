package com.ksptool.ourcraft.server.world.gen.layers;

import com.ksptool.ourcraft.sharedcore.BlockType;
import com.ksptool.ourcraft.sharedcore.blocks.inner.SharedBlock;
import com.ksptool.ourcraft.server.world.ServerChunk;
import com.ksptool.ourcraft.server.world.gen.GenerationContext;
import com.ksptool.ourcraft.server.world.gen.ITerrainLayer;

/**
 * 基础密度层，生成世界的3D基础形状（石头和空气）
 */
public class BaseDensityLayer implements ITerrainLayer {

    //空气状态ID
    private static final int AIR_STATE_ID = 0;

    @Override
    public void apply(int[][][] chunkData, int chunkX, int chunkZ, GenerationContext context) {
        SharedBlock stoneSharedBlock = context.getRegistry().getBlock(BlockType.STONE.getNamespacedId());
        if (stoneSharedBlock == null) {
            return;
        }
        
        int stoneStateId = context.getGlobalPalette().getStateId(stoneSharedBlock.getDefaultState());

        for (int x = 0; x < ServerChunk.CHUNK_SIZE; x++) {
            for (int y = 0; y < ServerChunk.CHUNK_HEIGHT; y++) {
                for (int z = 0; z < ServerChunk.CHUNK_SIZE; z++) {
                    int worldX = chunkX * ServerChunk.CHUNK_SIZE + x;
                    int worldY = y;
                    int worldZ = chunkZ * ServerChunk.CHUNK_SIZE + z;

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

