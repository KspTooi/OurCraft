package com.ksptool.mycraft.world.save;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 玩家数据索引类，用于存储玩家数据
 */
public class PlayerIndex {
    public UUID uuid;
    public float posX;
    public float posY;
    public float posZ;
    public float yaw;
    public float pitch;
    public float health;
    public int selectedSlot;
    public List<ItemStackData> hotbar;

    public PlayerIndex() {
        this.hotbar = new ArrayList<>();
    }
}

