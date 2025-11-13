package com.ksptool.mycraft.world;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.BlockingQueue;

/**
 * 世界生成线程类，在后台线程中生成区块数据
 */
@Slf4j
public class WorldGenerator extends Thread {
    private final BlockingQueue<ChunkGenerationTask> generationQueue;
    private final World world;
    private volatile boolean running = true;

    public WorldGenerator(World world, BlockingQueue<ChunkGenerationTask> generationQueue) {
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
                if (task == null) {
                    continue;
                }

                Chunk chunk = new Chunk(task.getChunkX(), task.getChunkZ());
                world.generateChunkData(chunk);
                chunk.setState(Chunk.ChunkState.DATA_LOADED);
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

