package com.ksptool.ourcraft.sharedcore.entity.components;

import org.joml.Vector3d;
import org.joml.Vector3f;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class TransformComponent implements EntityComponent {

    public Vector3d position = new Vector3d();

    public Vector3d prevPosition = new Vector3d(); // 用于插值

    public double yaw = 0.0f;

    public double pitch = 0.0f;
    
    public TransformComponent(Vector3f startPos) {
        this.position.set(startPos);
        this.prevPosition.set(startPos);
    }

}