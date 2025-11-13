package com.ksptool.mycraft.item;

import lombok.Getter;

/**
 * 物品栏管理类，管理玩家的快捷栏和物品选择
 */
@Getter
public class Inventory {

    private static final int HOTBAR_SIZE = 9;

    private ItemStack[] hotbar;

    private int selectedSlot;

    public Inventory() {
        this.hotbar = new ItemStack[HOTBAR_SIZE];
        this.selectedSlot = 0;
        initializeDefaultItems();
    }

    private void initializeDefaultItems() {
        hotbar[0] = new ItemStack(Item.getItem(1), 64);
        hotbar[1] = new ItemStack(Item.getItem(2), 64);
        hotbar[2] = new ItemStack(Item.getItem(3), 64);
        hotbar[3] = new ItemStack(Item.getItem(4), 64);
        hotbar[4] = new ItemStack(Item.getItem(5), 64);
    }

    public ItemStack getSelectedItem() {
        if (selectedSlot >= 0 && selectedSlot < HOTBAR_SIZE) {
            ItemStack stack = hotbar[selectedSlot];
            if (stack != null && !stack.isEmpty()) {
                return stack;
            }
        }
        return null;
    }

    public void setSelectedSlot(int slot) {
        if (slot >= 0 && slot < HOTBAR_SIZE) {
            this.selectedSlot = slot;
        }
    }

    public void scrollSelection(int delta) {
        selectedSlot = (selectedSlot + delta + HOTBAR_SIZE) % HOTBAR_SIZE;
    }
}

