package com.ksptool.ourcraft.sharedcore.blocks;

import com.ksptool.ourcraft.sharedcore.blocks.inner.SharedBlock;
import com.ksptool.ourcraft.sharedcore.world.BlockState;
import com.ksptool.ourcraft.sharedcore.world.SharedWorld;
import com.ksptool.ourcraft.sharedcore.BlockType;
import com.ksptool.ourcraft.sharedcore.world.properties.EnumProperty;

/**
 * 木头方块类，具有方向轴属性
 */
public class WoodBlock extends SharedBlock {
    public static final EnumProperty<Axis> AXIS = EnumProperty.create("axis", Axis.class);

    public enum Axis {
        X, Y, Z
    }

    public WoodBlock() {
        super(BlockType.WOOD.getStdRegName(), 2.0f, 0);
    }

    @Override
    protected void defineProperties() {
        addProperty(AXIS);
    }

    @Override
    public String getTextureName(SharedWorld world, int face, BlockState state) {
        if (world.isServerSide()) {
            return null;
        }
        Axis axis = state.get(AXIS);
        if (axis == Axis.Y) {
            if (face == 0 || face == 1) {
                return "log_oak_top.png";
            }
            return "log_oak.png";
        }
        return "log_oak.png";
    }
}
