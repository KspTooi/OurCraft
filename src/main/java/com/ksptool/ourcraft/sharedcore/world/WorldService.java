package com.ksptool.ourcraft.sharedcore.world;

import lombok.Getter;
import lombok.Setter;

/**
 * 世界服务基类，所有世界服务都必须继承该类
 */
@Getter
public abstract class WorldService implements SequenceUpdate,Comparable<WorldService>{

    //服务优先级
    @Setter
    private int priority = 0;

    //服务是否就绪
    protected boolean isReady = false;

    /**
     * 初始化或重新加载服务 在服务被加载或重新加载时调用
     */
    public void initOrReload(){
        isReady = true;
    }

    @Override
    public int compareTo(WorldService o) {
        return Integer.compare(this.priority, o.priority);
    }

}
