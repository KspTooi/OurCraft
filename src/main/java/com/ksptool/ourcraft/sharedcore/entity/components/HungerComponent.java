package com.ksptool.ourcraft.sharedcore.entity.components;

import lombok.Getter;
import lombok.Setter;

@Getter@Setter
public class HungerComponent implements EntityComponent{

    private int maxHunger;

    private int hunger;

}
