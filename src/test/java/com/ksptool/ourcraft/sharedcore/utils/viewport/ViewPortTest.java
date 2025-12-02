package com.ksptool.ourcraft.sharedcore.utils.viewport;

import com.ksptool.ourcraft.sharedcore.utils.position.ChunkPos;
import org.junit.jupiter.api.Test;

public class ViewPortTest {

    @Test
    public void testChunkViewPort() {
        var chunkViewPort = ChunkViewPort.of(ChunkPos.of(0, 0, 0), 3);
        var chunkPosSet = chunkViewPort.getChunkPosSet();
        System.out.println(chunkPosSet);
    }

    @Test
    public void testChunkViewPortVisualizer() {
        var chunkViewPortVisualizer = new ChunkViewPortVisualizer();
        chunkViewPortVisualizer.main();
    }

}
