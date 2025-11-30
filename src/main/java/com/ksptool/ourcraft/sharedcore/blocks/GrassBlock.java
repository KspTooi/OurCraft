package com.ksptool.ourcraft.sharedcore.blocks;

import com.ksptool.ourcraft.sharedcore.blocks.inner.SharedBlock;
import com.ksptool.ourcraft.sharedcore.enums.BlockEnums;
import com.ksptool.ourcraft.sharedcore.world.BlockState;
import com.ksptool.ourcraft.sharedcore.world.SharedWorld;
import com.ksptool.ourcraft.sharedcore.world.properties.BooleanProperty;

/**
 * 草方块类，具有雪化属性
 */
public class GrassBlock extends SharedBlock {
    public static final BooleanProperty SNOWY = BooleanProperty.create("snowy");

    public GrassBlock() {
        super(BlockEnums.GRASS_BLOCK.getStdRegName(), 0.6f, 0);
    }

    @Override
    protected void defineProperties() {
        addProperty(SNOWY);
    }

    @Override
    public String getTextureName(SharedWorld world, int face, BlockState state) {
        if (world.isServerSide()) {
            return null;
        }
        if (face == 0) {
            return "grass_top.png";
            //return "oc_gb_top.png";
        }
        if (face == 1) {
            return "dirt.png";
            //return "oc_gb_dirt.png";
        }
        return "grass_side.png";
        //return "oc_gb_side.png";
    }
}
