package com.ksptool.mycraft.world.properties;

import java.util.Arrays;

/**
 * 枚举属性类，表示方块的枚举类型属性
 */
public class EnumProperty<E extends Enum<E>> extends BlockProperty<E> {

    public EnumProperty(String name, Class<E> enumClass) {
        super(name, enumClass, Arrays.asList(enumClass.getEnumConstants()));
    }

    public static <E extends Enum<E>> EnumProperty<E> create(String name, Class<E> enumClass) {
        return new EnumProperty<>(name, enumClass);
    }
}

