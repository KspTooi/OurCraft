package com.ksptool.ourcraft.server.world.gen.layers;

import com.ksptool.ourcraft.sharedcore.enums.BlockEnums;
import com.ksptool.ourcraft.sharedcore.GlobalPalette;
import com.ksptool.ourcraft.sharedcore.blocks.inner.SharedBlock;
import com.ksptool.ourcraft.sharedcore.world.BlockState;
import com.ksptool.ourcraft.sharedcore.world.gen.GenerationContext;
import com.ksptool.ourcraft.sharedcore.world.gen.TerrainLayer;
import com.ksptool.ourcraft.server.world.ServerWorld;
import com.ksptool.ourcraft.server.world.gen.TreeGenerator;

import java.util.Random;

/**
 * 地物层，负责在世界中生成树木等结构
 */
public class FeatureLayer implements TerrainLayer {
    
    private static final int AIR_STATE_ID = 0;
    private static final int SAFE_MARGIN = 3;
    private static final double TREE_DENSITY_THRESHOLD = 0.95;

    @Override
    public void apply(int[][][] chunkData, int chunkX, int chunkZ, GenerationContext context) {
        SharedBlock grassSharedBlock = context.getRegistry().getBlock(BlockEnums.GRASS_BLOCK.getStdRegName());
        if (grassSharedBlock == null) {
            return;
        }

        int grassStateId = context.getGlobalPalette().getStateId(grassSharedBlock.getDefaultState());

        Random random = new Random(context.getNumericSeed() + chunkX * 31L + chunkZ * 17L);

        var t = ((ServerWorld)context.getWorld()).getTemplate();
        var chunkSizeX = t.getChunkSizeX();
        var chunkSizeZ = t.getChunkSizeZ();

        for (int x = SAFE_MARGIN; x < chunkSizeX - SAFE_MARGIN; x++) {
            for (int z = SAFE_MARGIN; z < chunkSizeZ - SAFE_MARGIN; z++) {
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
        GlobalPalette palette = context.getGlobalPalette();
        var t = ((ServerWorld)context.getWorld()).getTemplate();
        var chunkSizeY = t.getChunkSizeY();
        for (int y = chunkSizeY - 1; y >= 0; y--) {
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

