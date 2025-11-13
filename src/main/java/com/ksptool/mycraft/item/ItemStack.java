package com.ksptool.mycraft.item;

import lombok.Getter;

/**
 * 物品堆栈类，表示一组相同物品及其数量
 */
@Getter
public class ItemStack {
    private Item item;
    private int count;

    public ItemStack(Item item, int count) {
        this.item = item;
        this.count = Math.min(count, item != null ? item.getMaxStackSize() : 0);
    }

    public ItemStack(Item item) {
        this(item, 1);
    }

    public void setCount(int count) {
        if (item != null) {
            this.count = Math.min(count, item.getMaxStackSize());
        } else {
            this.count = 0;
        }
    }

    public void add(int amount) {
        if (item != null) {
            this.count = Math.min(this.count + amount, item.getMaxStackSize());
        }
    }

    public void remove(int amount) {
        this.count = Math.max(0, this.count - amount);
        if (this.count == 0) {
            this.item = null;
        }
    }

    public boolean isEmpty() {
        return item == null || count == 0;
    }

    public boolean isFull() {
        return item != null && count >= item.getMaxStackSize();
    }
}

