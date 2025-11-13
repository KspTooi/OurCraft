package com.ksptool.mycraft.world.properties;

import java.util.Arrays;
import java.util.Collection;

/**
 * 布尔属性类，表示方块的布尔类型属性
 */
public class BooleanProperty extends BlockProperty<Boolean> {

    //布尔属性值列表
    public static final Collection<Boolean> BOOLEAN_VALUES = Arrays.asList(false, true);

    public BooleanProperty(String name) {
        super(name, Boolean.class, BOOLEAN_VALUES);
    }

    public static BooleanProperty create(String name) {
        return new BooleanProperty(name);
    }
}

