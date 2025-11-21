package com.ksptool.ourcraft.sharedcore.entity.components;

import lombok.Getter;
import lombok.Setter;

@Getter@Setter
public class InputComponent implements EntityComponent {

    private boolean w;

    private boolean s;

    private boolean a;

    private boolean d;

    private boolean jump;

    private boolean sneak;
    
    public void reset() {
        w = s = a = d = jump = sneak = false;
    }
}