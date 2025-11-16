package com.ksptool.ourcraft.sharedcore.network.packets;

/**
 * 玩家动作类型枚举
 */
public enum ActionType {
    /**
     * 开始破坏方块
     */
    START_BREAKING,
    
    /**
     * 结束破坏方块
     */
    FINISH_BREAKING,
    
    /**
     * 放置方块
     */
    PLACE_BLOCK,
    
    /**
     * 使用物品
     */
    USE_ITEM
}

