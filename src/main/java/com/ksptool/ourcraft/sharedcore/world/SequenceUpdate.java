package com.ksptool.ourcraft.sharedcore.world;

/**
 * 序列化更新接口，用于驱动世界逻辑的执行，所有需要按顺序更新的类都必须实现该接口
 */
public interface SequenceUpdate {

    /**
     * 执行更新
     * @param delta 距离上一帧经过的时间（秒）
     * @param world 世界
     */
    void action(double delta,SharedWorld world);

}
