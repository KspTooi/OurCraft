package com.ksptool.ourcraft.server.world.gen;

import com.ksptool.ourcraft.sharedcore.GlobalPalette;
import com.ksptool.ourcraft.server.world.ServerWorld;
import com.ksptool.ourcraft.server.world.NoiseGenerator;
import com.ksptool.ourcraft.sharedcore.Registry;

/**
 * 地形生成上下文，用于在地形层之间传递共享资源
 */
public class GenerationContext {

    //噪声生成器
    private final NoiseGenerator noiseGenerator;

    //世界
    private final ServerWorld world;

    //种子
    private final String seed;

    //数值种子（用于Random初始化）
    private final long numericSeed;

    //全局调色板
    private final GlobalPalette globalPalette;
    
    //注册表
    private final Registry registry;

    public GenerationContext(NoiseGenerator noiseGenerator, ServerWorld world, String seed) {
        this.noiseGenerator = noiseGenerator;
        this.world = world;
        this.seed = seed;
        this.numericSeed = parseSeed(seed);
        this.globalPalette = GlobalPalette.getInstance();
        this.registry = Registry.getInstance();
    }

    public NoiseGenerator getNoiseGenerator() {
        return noiseGenerator;
    }

    public ServerWorld getWorld() {
        return world;
    }

    public String getSeed() {
        return seed;
    }

    public long getNumericSeed() {
        return numericSeed;
    }

    private long parseSeed(String seed) {
        if (seed == null || seed.isEmpty()) {
            return new java.util.Random().nextLong();
        }
        try {
            return Long.parseLong(seed);
        } catch (NumberFormatException e) {
            return (long) seed.hashCode();
        }
    }

    public GlobalPalette getGlobalPalette() {
        return globalPalette;
    }

    public Registry getRegistry() {
        return registry;
    }
}

