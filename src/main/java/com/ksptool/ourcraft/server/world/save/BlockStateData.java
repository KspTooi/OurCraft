package com.ksptool.ourcraft.server.world.save;

import com.ksptool.ourcraft.sharedcore.StdRegName;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * 方块状态数据类，用于 JSON 序列化/反序列化
 */
@Getter
@Setter
public class BlockStateData {

    private String stdRegName;

    private Map<String, String> properties;

    public BlockStateData() {
        this.properties = new HashMap<>();
    }

    public BlockStateData(String stdRegName, Map<String, String> properties) {
        this.stdRegName = stdRegName;
        this.properties = properties != null ? new HashMap<>(properties) : new HashMap<>();
    }

    public BlockStateData(StdRegName stdRegName, Map<String, String> properties) {
        this(stdRegName.getValue(), properties);
    }
}

