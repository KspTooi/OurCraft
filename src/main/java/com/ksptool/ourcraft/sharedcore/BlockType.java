package com.ksptool.ourcraft.sharedcore;

import com.ksptool.ourcraft.sharedcore.block.SharedBlock;
import com.ksptool.ourcraft.sharedcore.blocks.*;
import com.ksptool.ourcraft.sharedcore.world.Registry;
import lombok.Getter;
import lombok.SneakyThrows;

/**
 * 方块类型枚举，管理所有方块的命名空间ID和注册逻辑
 */
@Getter
public enum BlockType {
    AIR("mycraft:air", AirBlock.class),
    GRASS_BLOCK("mycraft:grass_block", GrassBlock.class),
    DIRT("mycraft:dirt", DirtBlock.class),
    STONE("mycraft:stone", StoneBlock.class),
    WOOD("mycraft:wood", WoodBlock.class),
    LEAVES("mycraft:leaves", LeavesBlock.class),
    WATER("mycraft:water", WaterBlock.class);

    private final String namespacedId;
    private final Class<? extends SharedBlock> blockClass;

    BlockType(String namespacedId, Class<? extends SharedBlock> blockClass) {
        this.namespacedId = namespacedId;
        this.blockClass = blockClass;
    }

    @SneakyThrows
    public SharedBlock createInstance() {
        return this.blockClass.getConstructor().newInstance();
    }

    public static void registerBlocks(Registry registry) {
        for (BlockType type : values()) {
            registry.register(type.createInstance());
        }
    }
}

