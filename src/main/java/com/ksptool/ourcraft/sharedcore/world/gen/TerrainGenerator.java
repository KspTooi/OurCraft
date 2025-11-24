package com.ksptool.ourcraft.sharedcore.world.gen;

import com.ksptool.ourcraft.sharedcore.StdRegName;
import com.ksptool.ourcraft.sharedcore.world.chunk.SharedChunk;

/**
 * 地形生成器接口，用于生成地形数据
 */
public interface TerrainGenerator {

    /**
     * 获取地形生成器的标准注册名
     * @return 标准化注册表名
     */
    StdRegName getStdRegName();

    /**
     * 执行地形生成
     * @param sharedChunk 共享区块
     * @param sharedWorld 共享世界
     */
    void execute(SharedChunk sharedChunk, GenerationContext sharedWorld);

}
