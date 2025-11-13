package com.ksptool.mycraft.world.gen.layers;

import com.ksptool.mycraft.world.Block;
import com.ksptool.mycraft.world.BlockState;
import com.ksptool.mycraft.world.Chunk;
import com.ksptool.mycraft.world.GlobalPalette;
import com.ksptool.mycraft.world.gen.GenerationContext;
import com.ksptool.mycraft.world.gen.ITerrainLayer;
import com.ksptool.mycraft.world.gen.TreeGenerator;

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
        Block grassBlock = context.getRegistry().get("mycraft:grass_block");
        if (grassBlock == null) {
            return;
        }

        int grassStateId = context.getGlobalPalette().getStateId(grassBlock.getDefaultState());

        Random random = new Random(context.getSeed() + chunkX * 31L + chunkZ * 17L);

        for (int x = SAFE_MARGIN; x < Chunk.CHUNK_SIZE - SAFE_MARGIN; x++) {
            for (int z = SAFE_MARGIN; z < Chunk.CHUNK_SIZE - SAFE_MARGIN; z++) {
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
        for (int y = Chunk.CHUNK_HEIGHT - 1; y >= 0; y--) {
            int stateId = chunkData[x][y][z];
            if (stateId == AIR_STATE_ID) {
                continue;
            }
            BlockState state = palette.getState(stateId);
            Block block = state.getBlock();
            if (block.isSolid() && !block.isFluid()) {
                return y;
            }
        }
        return -1;
    }
}

