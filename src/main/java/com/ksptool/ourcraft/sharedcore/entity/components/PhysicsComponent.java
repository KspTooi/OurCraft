
package com.ksptool.ourcraft.sharedcore.entity.components;

import com.ksptool.ourcraft.sharedcore.BoundingBox;
import org.joml.Vector3d;
import org.joml.Vector3f;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class PhysicsComponent implements EntityComponent {

    private Vector3d velocity = new Vector3d();

    private BoundingBox boundingBox;

    private boolean onGround = false;

    private boolean hasGravity = true;

    private double gravityMultiplier = -20.0f;

    public PhysicsComponent(float width, float height) {
        this.boundingBox = new BoundingBox(new Vector3f(), width, height);
    }
}