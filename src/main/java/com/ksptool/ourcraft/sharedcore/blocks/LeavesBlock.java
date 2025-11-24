package com.ksptool.ourcraft.sharedcore.blocks;

import com.ksptool.ourcraft.sharedcore.blocks.inner.SharedBlock;
import com.ksptool.ourcraft.sharedcore.world.BlockState;
import com.ksptool.ourcraft.sharedcore.world.SharedWorld;
import com.ksptool.ourcraft.sharedcore.enums.BlockEnums;

/**
 * 树叶方块类
 */
public class LeavesBlock extends SharedBlock {
    public LeavesBlock() {
        super(BlockEnums.LEAVES.getStdRegName(), 0.2f, 0);
    }

    @Override
    protected void defineProperties() {
    }

    @Override
    public String getTextureName(SharedWorld world, int face, BlockState state) {
        if (world.isServerSide()) {
            return null;
        }
        return "leaves_oak.png";
    }
}
