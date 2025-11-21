package com.ksptool.ourcraft.sharedcore.template;

import com.ksptool.ourcraft.sharedcore.StdRegName;

import lombok.Getter;

/**
 * 物品模板类，定义物品的基本属性
 */
public abstract class ItemTemplate {

    @Getter
    private final StdRegName stdRegName;

    private int durability;

    private int stackSize;

    //质量(以KG计) 默认1KG
    private double mass = 1;

    //体积(以L计)  默认1L
    private double volume = 1;

    public ItemTemplate(StdRegName stdRegName) {
        this.stdRegName = stdRegName;
    }

}
