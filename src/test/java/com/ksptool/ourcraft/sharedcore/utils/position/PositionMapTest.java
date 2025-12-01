package com.ksptool.ourcraft.sharedcore.utils.position;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;


/**
 * 位置组件测试类
 * 测试 Pos、ChunkPos、LocalPos、ScaPos 的Map功能
 */
public class PositionMapTest {

    private static final Map<Pos, String> posMap = new HashMap<>();
    private static final Map<ChunkPos, String> chunkPosMap = new HashMap<>();
    private static final Map<ChunkLocalPos, String> chunkLocalPosMap = new HashMap<>();
    private static final Map<ScaPos, String> scaPosMap = new HashMap<>();
    /**
     * 世界坐标测试
     */
    @Test
    public void posMapTest() {

        var pos = Pos.of(100, 100, 100);
        posMap.put(pos, "test");

        var equalsPos = Pos.of(100, 100, 100);

        if(posMap.containsKey(equalsPos)){
            System.out.println("坐标相同");
            return;
        }

        System.out.println("坐标不同");

    }
}

