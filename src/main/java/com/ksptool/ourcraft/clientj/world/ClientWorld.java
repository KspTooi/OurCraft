package com.ksptool.ourcraft.clientj.world;

import com.ksptool.ourcraft.sharedcore.world.SharedWorld;
import com.ksptool.ourcraft.sharedcore.world.WorldTemplate;

public class ClientWorld implements SharedWorld {


    @Override
    public boolean isServerSide() {
        return false;
    }

    @Override
    public boolean isClientSide() {
        return true;
    }

    @Override
    public void action(double delta) {

    }

    public String getName() {
        return "ClientWorld";
    }

    public WorldTemplate getTemplate() {
        return null;
    }

}
