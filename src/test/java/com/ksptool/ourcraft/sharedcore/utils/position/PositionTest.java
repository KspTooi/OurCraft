package com.ksptool.ourcraft.sharedcore.utils.position;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 位置组件测试类
 * 测试 Pos、ChunkPos、LocalPos、ScaPos 之间的转换功能
 */
public class PositionTest {


    private static final int DEFAULT_CHUNK_SIZE_X = 16;

    private static final int DEFAULT_CHUNK_SIZE_Z = 16;

    private static final int DEFAULT_SCA_PKG_SIZE = 40;

    /**
     * 世界坐标测试
     */
    @Test
    public void worldPosTest() {
        Pos worldPos = Pos.of(35, 64, 20);
        System.out.println(worldPos);
    }

    /**
     * 世界坐标转区块坐标测试
     */
    @Test
    public void worldPosToChunkPosTest() {
        Pos worldPos = Pos.of(1800000000, 64, 0);
        ChunkPos chunkPos = worldPos.toChunkPos(DEFAULT_CHUNK_SIZE_X, DEFAULT_CHUNK_SIZE_Z);
        System.out.println(chunkPos);

        assertEquals(112500000, chunkPos.getX());

        worldPos = Pos.of(-1800000000, 64, 0);
        chunkPos = worldPos.toChunkPos(DEFAULT_CHUNK_SIZE_X, DEFAULT_CHUNK_SIZE_Z);
        System.out.println(chunkPos);

        assertEquals(-112500000, chunkPos.getX());
    }

    /**
     * 世界坐标转区块局部坐标测试
     */
    @Test
    public void worldPosToLocalPosTest() {
        Pos worldPos = Pos.of(254, 64, 0);
        ChunkLocalPos chunkLocalPos = worldPos.toLocalPos(DEFAULT_CHUNK_SIZE_X, DEFAULT_CHUNK_SIZE_Z);
        System.out.println(chunkLocalPos);
    }

    /**
     * 世界坐标转SCA封装坐标测试
     */
    @Test
    public void worldPosToScaPosTest() {
        Pos worldPos = Pos.of(1800000000, 64, 0);
        ScaPos scaPos = worldPos.toScaPos(DEFAULT_SCA_PKG_SIZE, DEFAULT_CHUNK_SIZE_X, DEFAULT_CHUNK_SIZE_Z);
        System.out.println(scaPos);
    }
}

