package com.ksptool.mycraft.world.save;

/**
 * 物品堆叠数据类，用于 JSON 序列化/反序列化
 */
public class ItemStackData {
    public Integer itemId;
    public Integer count;

    public ItemStackData() {
    }

    public ItemStackData(Integer itemId, Integer count) {
        this.itemId = itemId;
        this.count = count;
    }
}

