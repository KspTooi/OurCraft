package com.ksptool.mycraft.world.gen;

import com.ksptool.mycraft.world.Chunk;

import java.util.ArrayList;
import java.util.List;

/**
 * 地形管道类，负责按顺序执行多个地形层
 */
public class TerrainPipeline {

    //地形层列表
    private final List<ITerrainLayer> layers;

    public TerrainPipeline() {
        this.layers = new ArrayList<>();
    }

    public void addLayer(ITerrainLayer layer) {
        if (layer == null) {
            return;
        }
        layers.add(layer);
    }

    //执行地形管道
    public void execute(Chunk chunk, GenerationContext context) {
        int[][][] chunkData = new int[Chunk.CHUNK_SIZE][Chunk.CHUNK_HEIGHT][Chunk.CHUNK_SIZE];
        
        for (ITerrainLayer layer : layers) {
            layer.apply(chunkData, chunk.getChunkX(), chunk.getChunkZ(), context);
        }
        
        for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
            for (int y = 0; y < Chunk.CHUNK_HEIGHT; y++) {
                for (int z = 0; z < Chunk.CHUNK_SIZE; z++) {
                    chunk.setBlockState(x, y, z, chunkData[x][y][z]);
                }
            }
        }
    }
}

