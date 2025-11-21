package com.ksptool.ourcraft.sharedcore.world;

import lombok.Builder;
import lombok.Value;

/**
 * 世界模板类，定义世界类型的静态配置
 */
@Value
@Builder
public class WorldTemplateOld {
    /**
     * 模板的唯一ID，用于注册和保存
     */
    String templateId;
    
    /**
     * 该世界类型每秒的逻辑更新次数
     */
    int ticksPerSecond;
    
    /**
     * 世界内的重力加速度
     */
    float gravity;
}

