package com.ksptool.ourcraft.sharedcore.entity.components;

import lombok.Getter;
import lombok.Setter;

@Getter@Setter
public class HealthComponent implements EntityComponent{

    private int maxHealth;

    private int health;

}
