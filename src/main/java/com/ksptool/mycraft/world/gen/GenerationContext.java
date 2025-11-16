package com.ksptool.mycraft.world.gen;

import com.ksptool.mycraft.world.GlobalPalette;
import com.ksptool.mycraft.server.world.ServerWorld;
import com.ksptool.mycraft.world.NoiseGenerator;
import com.ksptool.mycraft.world.Registry;

/**
 * 地形生成上下文，用于在地形层之间传递共享资源
 */
public class GenerationContext {

    //噪声生成器
    private final NoiseGenerator noiseGenerator;

    //世界
    private final ServerWorld world;

    //种子
    private final long seed;

    //全局调色板
    private final GlobalPalette globalPalette;
    
    //注册表
    private final Registry registry;

    public GenerationContext(NoiseGenerator noiseGenerator, ServerWorld world, long seed) {
        this.noiseGenerator = noiseGenerator;
        this.world = world;
        this.seed = seed;
        this.globalPalette = GlobalPalette.getInstance();
        this.registry = Registry.getInstance();
    }

    public NoiseGenerator getNoiseGenerator() {
        return noiseGenerator;
    }

    public ServerWorld getWorld() {
        return world;
    }

    public long getSeed() {
        return seed;
    }

    public GlobalPalette getGlobalPalette() {
        return globalPalette;
    }

    public Registry getRegistry() {
        return registry;
    }
}

