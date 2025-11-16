package com.ksptool.mycraft.world.gen.layers;

import com.ksptool.mycraft.sharedcore.BlockType;
import com.ksptool.mycraft.world.Block;
import com.ksptool.mycraft.server.world.ServerChunk;
import com.ksptool.mycraft.world.gen.GenerationContext;
import com.ksptool.mycraft.world.gen.ITerrainLayer;

/**
 * 地表层，生成草地和泥土层
 */
public class SurfaceLayer implements ITerrainLayer {
    private static final int AIR_STATE_ID = 0;

    @Override
    public void apply(int[][][] chunkData, int chunkX, int chunkZ, GenerationContext context) {
        Block grassBlock = context.getRegistry().get(BlockType.GRASS_BLOCK.getNamespacedId());
        Block dirtBlock = context.getRegistry().get(BlockType.DIRT.getNamespacedId());
        Block stoneBlock = context.getRegistry().get(BlockType.STONE.getNamespacedId());
        
        if (grassBlock == null || dirtBlock == null || stoneBlock == null) {
            return;
        }
        
        int grassStateId = context.getGlobalPalette().getStateId(grassBlock.getDefaultState());
        int dirtStateId = context.getGlobalPalette().getStateId(dirtBlock.getDefaultState());
        int stoneStateId = context.getGlobalPalette().getStateId(stoneBlock.getDefaultState());

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

