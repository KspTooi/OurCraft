package com.ksptool.ourcraft.server.entity;

import com.ksptool.ourcraft.sharedcore.StdRegName;
import com.ksptool.ourcraft.sharedcore.world.SharedWorld;
import lombok.Getter;
import org.joml.Vector3d;

public class EcsEntityService {

    @Getter
    private final SharedWorld world;

    public EcsEntityService(SharedWorld world) {
        this.world = world;
    }

    public void createEntity(StdRegName regName, Vector3d pos) {
        
    }

}
