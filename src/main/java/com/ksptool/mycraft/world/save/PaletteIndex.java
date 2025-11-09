package com.ksptool.mycraft.world.save;

import java.util.ArrayList;
import java.util.List;

/**
 * 调色板索引类，用于存储调色板的 JSON 序列化数据
 */
public class PaletteIndex {
    public List<BlockStateData> states;

    public PaletteIndex() {
        this.states = new ArrayList<>();
    }
}

