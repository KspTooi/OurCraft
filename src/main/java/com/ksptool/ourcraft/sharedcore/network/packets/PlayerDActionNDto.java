package com.ksptool.ourcraft.sharedcore.network.packets;

/**
 * 玩家声明动作 (Player Declare Action Network Data Transfer Object)
 * 如开始破坏方块、结束破坏方块、放置方块或使用物品
 */
public record PlayerDActionNDto(ActionType actionType, int x, int y, int z, int face) {}

