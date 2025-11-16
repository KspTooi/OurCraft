package com.ksptool.ourcraft.server.world.save;

/**
 * 世界元数据类，存储单个世界的信息
 */
public class WorldMetadata {
    public String name;
    public long seed;
    public long worldTime;
    public String templateId;

    public WorldMetadata() {
    }

    public WorldMetadata(String name, long seed, long worldTime) {
        this.name = name;
        this.seed = seed;
        this.worldTime = worldTime;
    }
}

