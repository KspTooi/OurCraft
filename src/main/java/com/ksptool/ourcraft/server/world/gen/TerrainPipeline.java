package com.ksptool.ourcraft.server.world.gen;

import com.ksptool.ourcraft.server.world.chunk.ServerChunk;

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
    public void execute(ServerChunk chunk, GenerationContext context) {
        int[][][] chunkData = new int[ServerChunk.CHUNK_SIZE][ServerChunk.CHUNK_HEIGHT][ServerChunk.CHUNK_SIZE];
        
        for (ITerrainLayer layer : layers) {
            layer.apply(chunkData, chunk.getChunkX(), chunk.getChunkZ(), context);
        }
        
        for (int x = 0; x < ServerChunk.CHUNK_SIZE; x++) {
            for (int y = 0; y < ServerChunk.CHUNK_HEIGHT; y++) {
                for (int z = 0; z < ServerChunk.CHUNK_SIZE; z++) {
                    chunk.setBlockState(x, y, z, chunkData[x][y][z]);
                }
            }
        }
    }
}

