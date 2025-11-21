package com.ksptool.ourcraft.server.world.gen.layers;

import com.ksptool.ourcraft.sharedcore.BlockType;
import com.ksptool.ourcraft.sharedcore.blocks.inner.SharedBlock;
import com.ksptool.ourcraft.server.world.ServerChunk;
import com.ksptool.ourcraft.server.world.gen.GenerationContext;
import com.ksptool.ourcraft.server.world.gen.ITerrainLayer;

/**
 * 地表层，生成草地和泥土层
 */
public class SurfaceLayer implements ITerrainLayer {
    private static final int AIR_STATE_ID = 0;

    @Override
    public void apply(int[][][] chunkData, int chunkX, int chunkZ, GenerationContext context) {
        SharedBlock grassSharedBlock = context.getRegistry().getBlock(BlockType.GRASS_BLOCK.getNamespacedId());
        SharedBlock dirtSharedBlock = context.getRegistry().getBlock(BlockType.DIRT.getNamespacedId());
        SharedBlock stoneSharedBlock = context.getRegistry().getBlock(BlockType.STONE.getNamespacedId());
        
        if (grassSharedBlock == null || dirtSharedBlock == null || stoneSharedBlock == null) {
            return;
        }
        
        int grassStateId = context.getGlobalPalette().getStateId(grassSharedBlock.getDefaultState());
        int dirtStateId = context.getGlobalPalette().getStateId(dirtSharedBlock.getDefaultState());
        int stoneStateId = context.getGlobalPalette().getStateId(stoneSharedBlock.getDefaultState());

        for (int x = 0; x < ServerChunk.CHUNK_SIZE; x++) {
            for (int z = 0; z < ServerChunk.CHUNK_SIZE; z++) {
                for (int y = ServerChunk.CHUNK_HEIGHT - 1; y >= 0; y--) {
                    if (chunkData[x][y][z] == stoneStateId) {
                        if (y + 1 < ServerChunk.CHUNK_HEIGHT && chunkData[x][y + 1][z] == AIR_STATE_ID) {
                            chunkData[x][y][z] = grassStateId;
                            
                            for (int depth = 1; depth <= 3 && y - depth >= 0; depth++) {
                                if (chunkData[x][y - depth][z] == stoneStateId) {
                                    chunkData[x][y - depth][z] = dirtStateId;
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

