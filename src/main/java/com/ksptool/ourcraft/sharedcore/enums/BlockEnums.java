package com.ksptool.ourcraft.sharedcore.enums;

import com.ksptool.ourcraft.sharedcore.StdRegName;
import com.ksptool.ourcraft.sharedcore.blocks.inner.SharedBlock;
import com.ksptool.ourcraft.sharedcore.blocks.*;
import com.ksptool.ourcraft.sharedcore.Registry;
import lombok.Getter;
import lombok.SneakyThrows;

/**
 * 方块类型枚举，管理所有方块的命名空间ID和注册逻辑
 */
@Getter
public enum BlockEnums {

    AIR(StdRegName.of("mycraft:air"), AirBlock.class),
    GRASS_BLOCK(StdRegName.of("mycraft:grass_block"), GrassBlock.class),
    DIRT(StdRegName.of("mycraft:dirt"), DirtBlock.class),
    STONE(StdRegName.of("mycraft:stone"), StoneBlock.class),
    WOOD(StdRegName.of("mycraft:wood"), WoodBlock.class),
    LEAVES(StdRegName.of("mycraft:leaves"), LeavesBlock.class),
    WATER(StdRegName.of("mycraft:water"), WaterBlock.class);

    private final StdRegName stdRegName;
    private final Class<? extends SharedBlock> blockClass;

    BlockEnums(StdRegName stdRegName, Class<? extends SharedBlock> blockClass) {
        if(stdRegName == null){
            throw new IllegalArgumentException("StdRegName is null!");
        }
        this.stdRegName = stdRegName;
        this.blockClass = blockClass;
    }

    /**
     * 创建方块实例
     * @return 方块实例
     */
    @SneakyThrows
    public SharedBlock createInstance() {
        return this.blockClass.getConstructor().newInstance();
    }

    /**
     * 注册所有引擎自带的默认方块
     * @param registry 注册表
     */
    public static void registerBlocks(Registry registry) {
        for (BlockEnums type : values()) {
            registry.registerBlock(type.createInstance());
        }
    }
}

