package com.ksptool.mycraft.world;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;

/**
 * 世界生成线程类，在后台线程中生成区块数据
 */
public class WorldGenerator extends Thread {
    private static final Logger logger = LoggerFactory.getLogger(WorldGenerator.class);
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
                logger.error("Error in WorldGenerator", e);
            }
        }
    }

    public void stopGenerator() {
        running = false;
        this.interrupt();
    }
}

