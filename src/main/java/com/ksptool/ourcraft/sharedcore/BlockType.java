package com.ksptool.ourcraft.sharedcore;

import com.ksptool.ourcraft.sharedcore.blocks.inner.SharedBlock;
import com.ksptool.ourcraft.sharedcore.blocks.*;
import com.ksptool.ourcraft.sharedcore.world.Registry;
import lombok.Getter;
import lombok.SneakyThrows;

/**
 * 方块类型枚举，管理所有方块的命名空间ID和注册逻辑
 */
@Getter
public enum BlockType {

    AIR(StdRegName.of("mycraft:air"), AirBlock.class),
    GRASS_BLOCK(StdRegName.of("mycraft:grass_block"), GrassBlock.class),
    DIRT(StdRegName.of("mycraft:dirt"), DirtBlock.class),
    STONE(StdRegName.of("mycraft:stone"), StoneBlock.class),
    WOOD(StdRegName.of("mycraft:wood"), WoodBlock.class),
    LEAVES(StdRegName.of("mycraft:leaves"), LeavesBlock.class),
    WATER(StdRegName.of("mycraft:water"), WaterBlock.class);

    private final StdRegName stdRegName;
    private final Class<? extends SharedBlock> blockClass;

    BlockType(StdRegName stdRegName, Class<? extends SharedBlock> blockClass) {
        if(stdRegName == null){
            throw new IllegalArgumentException("StdRegName is null!");
        }
        this.stdRegName = stdRegName;
        this.blockClass = blockClass;
    }

    @SneakyThrows
    public SharedBlock createInstance() {
        return this.blockClass.getConstructor().newInstance();
    }

    public static void registerBlocks(Registry registry) {
        for (BlockType type : values()) {
            registry.registerBlock(type.createInstance());
        }
    }
}

