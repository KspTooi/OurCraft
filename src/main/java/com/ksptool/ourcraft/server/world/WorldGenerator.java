package com.ksptool.ourcraft.server.world;

import com.ksptool.ourcraft.server.world.chunk.ServerChunk;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.BlockingQueue;

/**
 * 世界生成线程类，在后台线程中生成区块数据
 */
@Slf4j
public class WorldGenerator extends Thread {
    private final BlockingQueue<ChunkGenerationTask> generationQueue;
    private final ServerWorld world;
    private volatile boolean running = true;

    public WorldGenerator(ServerWorld world, BlockingQueue<ChunkGenerationTask> generationQueue) {
        this.world = world;
        this.generationQueue = generationQueue;
        this.setDaemon(true);
        this.setName("WorldGenerator");
    }

    @Override
    public void run() {
        while (running) {
            try {
                ChunkGenerationTask task = generationQueue.take();

                ServerChunk chunk = new ServerChunk(task.getChunkX(), task.getChunkZ());
                world.generateChunkData(chunk);
                chunk.setState(ServerChunk.ChunkState.DATA_LOADED);
                task.setChunk(chunk);
                task.setDataGenerated(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error in WorldGenerator: {}", e.getMessage(), e);
            }
        }
    }

    public void stopGenerator() {
        running = false;
        this.interrupt();
    }
}

