package com.ksptool.ourcraft.server.world.chunk.gen;

import java.util.concurrent.BlockingQueue;

import com.ksptool.ourcraft.server.world.chunk.FlexServerChunk;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Slf4j
public class FlexChunkGenerationThread implements Runnable {

    //区块生成队列
    private final BlockingQueue<FlexChunkGenTask> generationQueue;

    public FlexChunkGenerationThread(BlockingQueue<FlexChunkGenTask> generationQueue) {
        this.generationQueue = generationQueue;
    }

    @Override
    public void run() {
        log.info("新区块生成线程 已就绪: {}", Thread.currentThread().getName());
        while(true){
            try {

                //获取区块生成任务
                FlexChunkGenTask task = generationQueue.take();
                var ssc = task.getChunk();
                var world = ssc.getWorld();
                var tg = world.getTerrainGenerator();
                tg.execute(ssc, world.getGenerationContext());
                ssc.setState(FlexServerChunk.ChunkState.FINISH_LOAD);
                log.info("区块生成完成: CX:{} CZ:{}", ssc.getX(), ssc.getZ());

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

}
