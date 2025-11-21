package com.ksptool.ourcraft.server.world.gen.layers;

import com.ksptool.ourcraft.sharedcore.BlockType;
import com.ksptool.ourcraft.sharedcore.blocks.inner.SharedBlock;
import com.ksptool.ourcraft.sharedcore.world.BlockState;
import com.ksptool.ourcraft.server.world.ServerChunk;
import com.ksptool.ourcraft.server.world.gen.GenerationContext;
import com.ksptool.ourcraft.server.world.gen.ITerrainLayer;
import com.ksptool.ourcraft.server.world.gen.TreeGenerator;

import java.util.Random;

/**
 * 地物层，负责在世界中生成树木等结构
 */
public class FeatureLayer implements ITerrainLayer {
    
    private static final int AIR_STATE_ID = 0;
    private static final int SAFE_MARGIN = 3;
    private static final double TREE_DENSITY_THRESHOLD = 0.95;

    @Override
    public void apply(int[][][] chunkData, int chunkX, int chunkZ, GenerationContext context) {
        SharedBlock grassSharedBlock = context.getRegistry().getBlock(BlockType.GRASS_BLOCK.getNamespacedId());
        if (grassSharedBlock == null) {
            return;
        }

        int grassStateId = context.getGlobalPalette().getStateId(grassSharedBlock.getDefaultState());

        Random random = new Random(context.getSeed() + chunkX * 31L + chunkZ * 17L);

        for (int x = SAFE_MARGIN; x < ServerChunk.CHUNK_SIZE - SAFE_MARGIN; x++) {
            for (int z = SAFE_MARGIN; z < ServerChunk.CHUNK_SIZE - SAFE_MARGIN; z++) {
                int surfaceY = findSurfaceY(chunkData, x, z, context);
                if (surfaceY < 0) {
                    continue;
                }

                if (chunkData[x][surfaceY][z] != grassStateId) {
                    continue;
                }

                double noise = random.nextDouble();
                if (noise > TREE_DENSITY_THRESHOLD) {
                    TreeGenerator.place(chunkData, x, surfaceY + 1, z, context);
                }
            }
        }
    }

    private int findSurfaceY(int[][][] chunkData, int x, int z, GenerationContext context) {
        com.ksptool.ourcraft.sharedcore.world.GlobalPalette palette = context.getGlobalPalette();
        for (int y = ServerChunk.CHUNK_HEIGHT - 1; y >= 0; y--) {
            int stateId = chunkData[x][y][z];
            if (stateId == AIR_STATE_ID) {
                continue;
            }
            BlockState state = palette.getState(stateId);
            SharedBlock sharedBlock = state.getSharedBlock();
            if (sharedBlock.isSolid() && !sharedBlock.isFluid()) {
                return y;
            }
        }
        return -1;
    }
}

