package com.ksptool.ourcraft.sharedcore.entity.components;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Getter@Setter
public class MoveComponent implements EntityComponent {

    private double jumpHeight;

    private double jumpVelocity;

    private double groundAcceleration;

    private double airAcceleration;

    private double maxWalkSpeed;

    private double maxSprintSpeed;


}