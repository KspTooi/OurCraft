package com.ksptool.mycraft.world.blocks;

import com.ksptool.mycraft.world.Block;
import com.ksptool.mycraft.world.BlockState;

public class WaterBlock extends Block {

    public WaterBlock() {
        super("mycraft:water", 0, 0);
    }

    @Override
    protected void defineProperties() {
        // 当前版本留空
    }

    @Override
    public String getTextureName(int face, BlockState state) {
        return "water_still.png";
    }

    @Override
    public boolean isSolid() {
        return false;
    }

    @Override
    public boolean isFluid() {
        return true;
    }
}

