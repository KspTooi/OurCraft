package com.ksptool.ourcraft.server.world.gen;

/**
 * 地形层接口，定义地形生成的标准接口
 */
public interface ITerrainLayer {
    /**
     * 对区块生成做出贡献
     * @param chunkData 一个临时的方块ID数组，用于读写
     * @param chunkX 区块坐标
     * @param chunkZ 区块坐标
     * @param context 包含噪声生成器、种子等
     */
    void apply(int[][][] chunkData, int chunkX, int chunkZ, GenerationContext context);
}

