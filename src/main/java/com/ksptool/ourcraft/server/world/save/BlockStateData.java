package com.ksptool.ourcraft.server.world.save;

import java.util.HashMap;
import java.util.Map;

/**
 * 方块状态数据类，用于 JSON 序列化/反序列化
 */
public class BlockStateData {
    public String blockId;
    public Map<String, String> properties;

    public BlockStateData() {
        this.properties = new HashMap<>();
    }

    public BlockStateData(String blockId, Map<String, String> properties) {
        this.blockId = blockId;
        this.properties = properties != null ? new HashMap<>(properties) : new HashMap<>();
    }
}

