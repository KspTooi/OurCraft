package com.ksptool.mycraft.world.properties;

import java.util.Collection;

/**
 * 方块属性基类，定义方块属性的基本结构
 */
public abstract class BlockProperty<T extends Comparable<T>> {

    //属性名称
    private final String name;

    //属性值类型
    private final Class<T> valueClass;

    //允许的属性值
    private final Collection<T> allowedValues;

    //构造函数
    protected BlockProperty(String name, Class<T> valueClass, Collection<T> allowedValues) {
        this.name = name;
        this.valueClass = valueClass;
        this.allowedValues = allowedValues;
    }

    public String getName() {
        return name;
    }

    public Class<T> getValueClass() {
        return valueClass;
    }

    public Collection<T> getAllowedValues() {
        return allowedValues;
    }

    public T getDefaultValue() {
        if (allowedValues.isEmpty()) {
            return null;
        }
        return allowedValues.iterator().next();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        BlockProperty<?> that = (BlockProperty<?>) obj;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return name;
    }
}

