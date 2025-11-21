package com.ksptool.ourcraft.sharedcore.item;

import com.ksptool.ourcraft.sharedcore.BlockType;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * 物品定义类，定义物品的基本属性
 */
@Getter
public class Item {
    private int id;
    private String name;
    private int maxStackSize;
    private String blockNamespacedID;

    private static final Map<Integer, Item> ITEM_REGISTRY = new HashMap<>();
    private static final int DEFAULT_MAX_STACK_SIZE = 64;

    static {
        registerItem(1, "Grass Block", BlockType.GRASS_BLOCK.getStdRegName().getValue());
        registerItem(2, "Dirt", BlockType.DIRT.getStdRegName().getValue());
        registerItem(3, "Stone", BlockType.STONE.getStdRegName().getValue());
        registerItem(4, "Wood", BlockType.WOOD.getStdRegName().getValue());
        registerItem(5, "Leaves", BlockType.LEAVES.getStdRegName().getValue());
    }

    private static void registerItem(int id, String name, String blockNamespacedID) {
        ITEM_REGISTRY.put(id, new Item(id, name, DEFAULT_MAX_STACK_SIZE, blockNamespacedID));
    }

    public Item(int id, String name, int maxStackSize, String blockNamespacedID) {
        this.id = id;
        this.name = name;
        this.maxStackSize = maxStackSize;
        this.blockNamespacedID = blockNamespacedID;
    }

    public static Item getItem(int id) {
        return ITEM_REGISTRY.get(id);
    }
}

