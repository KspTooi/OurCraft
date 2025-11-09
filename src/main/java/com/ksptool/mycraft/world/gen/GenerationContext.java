package com.ksptool.mycraft.world.gen;

import com.ksptool.mycraft.world.GlobalPalette;
import com.ksptool.mycraft.world.NoiseGenerator;
import com.ksptool.mycraft.world.Registry;
import com.ksptool.mycraft.world.World;

/**
 * 地形生成上下文，用于在地形层之间传递共享资源
 */
public class GenerationContext {
    private final NoiseGenerator noiseGenerator;
    private final World world;
    private final long seed;
    private final GlobalPalette globalPalette;
    private final Registry registry;

    public GenerationContext(NoiseGenerator noiseGenerator, World world, long seed) {
        this.noiseGenerator = noiseGenerator;
        this.world = world;
        this.seed = seed;
        this.globalPalette = GlobalPalette.getInstance();
        this.registry = Registry.getInstance();
    }

    public NoiseGenerator getNoiseGenerator() {
        return noiseGenerator;
    }

    public World getWorld() {
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

