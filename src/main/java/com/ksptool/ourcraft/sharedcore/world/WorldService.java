package com.ksptool.ourcraft.sharedcore.world;

import com.ksptool.ourcraft.sharedcore.StdRegName;
import lombok.Getter;
import lombok.Setter;

/**
 * 世界服务基类，所有世界服务都必须继承该类
 */
@Getter
public abstract class WorldService implements SequenceUpdate,Comparable<WorldService>{

    //服务名称
    private final StdRegName name;

    //服务优先级
    @Setter
    private int priority = 0;

    public WorldService(StdRegName name) {
        if(name == null){
            throw new IllegalArgumentException("服务名称不能为空");
        }
        this.name = name;
    }

    public WorldService(String name) {
        this(StdRegName.of(name));
    }

    @Override
    public int compareTo(WorldService o) {
        return Integer.compare(this.priority, o.priority);
    }

}
