package com.ksptool.ourcraft.sharedcore.world.gen;

import com.ksptool.ourcraft.sharedcore.StdRegName;
import com.ksptool.ourcraft.sharedcore.blocks.inner.SharedBlock;
import com.ksptool.ourcraft.sharedcore.enums.BlockEnums;
import com.ksptool.ourcraft.sharedcore.world.SharedChunk;
import lombok.extern.slf4j.Slf4j;

/**
 * 出生平台生成器
 * 只在出生点(0,0)生成一小块草皮平台，其他地方都是虚空
 */
@Slf4j
public class SpawnPlatformGenerator implements TerrainGenerator {

    @Override
    public StdRegName getStdRegName() {
        return StdRegName.of("ourcraft:terrain_generator:spawn_platform");
    }

    @Override
    public void execute(SharedChunk chunk, GenerationContext context) {
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();

        // 只在出生点区块(0,0)生成
        if (chunkX != 0 || chunkZ != 0) {
            return;
        }

        // 获取方块
        SharedBlock dirtBlock = context.getRegistry().getBlock(BlockEnums.DIRT.getStdRegName());
        SharedBlock grassBlock = context.getRegistry().getBlock(BlockEnums.GRASS_BLOCK.getStdRegName());
        
        if (dirtBlock == null || grassBlock == null) {
            log.info("No dirt or grass block found");
            return;
        }
        
        // 获取方块状态ID
        int dirtStateId = context.getGlobalPalette().getStateId(dirtBlock.getDefaultState());
        int grassStateId = context.getGlobalPalette().getStateId(grassBlock.getDefaultState());

        // 在整个出生区块生成平台 (16x16)
        int platformY = 64; // 平台高度

        for (int x = 0; x < chunk.getSizeX(); x++) {
            for (int z = 0; z < chunk.getSizeZ(); z++) {
                // 底部4层泥土（更稳固）
                for (int y = platformY - 4; y < platformY; y++) {
                    if (y >= 0 && y < chunk.getSizeY()) {
                        chunk.setBlockState(x, y, z, dirtStateId);
                    }
                }
                
                // 顶部1层草方块
                if (platformY >= 0 && platformY < chunk.getSizeY()) {
                    chunk.setBlockState(x, platformY, z, grassStateId);
                }
            }
        }
        
        log.info("生成出生平台: 区块({}, {}) 在高度 Y={}", chunkX, chunkZ, platformY);
    }
}

