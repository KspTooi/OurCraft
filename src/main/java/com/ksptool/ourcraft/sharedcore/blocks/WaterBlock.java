package com.ksptool.ourcraft.sharedcore.blocks;

import com.ksptool.ourcraft.sharedcore.blocks.inner.SharedBlock;
import com.ksptool.ourcraft.sharedcore.world.BlockState;
import com.ksptool.ourcraft.sharedcore.world.SharedWorld;
import com.ksptool.ourcraft.sharedcore.BlockType;

public class WaterBlock extends SharedBlock {

    public WaterBlock() {
        super(BlockType.WATER.getStdRegName(), 0, 0);
    }

    @Override
    protected void defineProperties() {
        // 当前版本留空
    }

    @Override
    public String getTextureName(SharedWorld world, int face, BlockState state) {
        if (world.isServerSide()) {
            return null;
        }
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

