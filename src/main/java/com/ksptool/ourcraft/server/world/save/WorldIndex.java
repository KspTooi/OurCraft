package com.ksptool.ourcraft.server.world.save;

import java.util.ArrayList;
import java.util.List;

/**
 * 世界索引类，对应 world.index 文件的结构
 */
public class WorldIndex {
    public List<WorldMetadata> worlds;

    public WorldIndex() {
        this.worlds = new ArrayList<>();
    }
}

