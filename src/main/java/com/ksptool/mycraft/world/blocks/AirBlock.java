package com.ksptool.mycraft.world.blocks;

import com.ksptool.mycraft.world.Block;
import com.ksptool.mycraft.world.BlockState;

/**
 * 空气方块类，表示空方块
 */
public class AirBlock extends Block {

    public AirBlock() {
        super("mycraft:air", 0, 0);
    }

    @Override
    protected void defineProperties() {
    }

    @Override
    public String getTextureName(int face, BlockState state) {
        return null;
    }

    @Override
    public boolean isSolid() {
        return false;
    }
}
