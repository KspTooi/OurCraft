package com.ksptool.mycraft.world.properties;

import java.util.ArrayList;
import java.util.Collection;

/**
 * 整数属性类，表示方块的整数类型属性
 */
public class IntegerProperty extends BlockProperty<Integer> {
    
    public IntegerProperty(String name, int min, int max) {
        super(name, Integer.class, createRange(min, max));
    }

    private static Collection<Integer> createRange(int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("min (" + min + ") must be <= max (" + max + ")");
        }
        Collection<Integer> values = new ArrayList<>();
        for (int i = min; i <= max; i++) {
            values.add(i);
        }
        return values;
    }

    public static IntegerProperty create(String name, int min, int max) {
        return new IntegerProperty(name, min, max);
    }
}

