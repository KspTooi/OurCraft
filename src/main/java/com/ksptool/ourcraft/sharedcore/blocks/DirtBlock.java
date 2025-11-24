package com.ksptool.ourcraft.sharedcore.blocks;

import com.ksptool.ourcraft.sharedcore.blocks.inner.SharedBlock;
import com.ksptool.ourcraft.sharedcore.enums.BlockEnums;
import com.ksptool.ourcraft.sharedcore.world.BlockState;
import com.ksptool.ourcraft.sharedcore.world.SharedWorld;

/**
 * 泥土方块类
 */
public class DirtBlock extends SharedBlock {

    public DirtBlock() {
        super(BlockEnums.DIRT.getStdRegName(), 0.5f, 0);
    }

    @Override
    protected void defineProperties() {
    }

    @Override
    public String getTextureName(SharedWorld world, int face, BlockState state) {
        if (world.isServerSide()) {
            return null;
        }
        return "dirt.png";
    }
}
