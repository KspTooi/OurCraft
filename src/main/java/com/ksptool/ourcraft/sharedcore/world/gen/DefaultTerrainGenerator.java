package com.ksptool.ourcraft.sharedcore.world.gen;

import com.ksptool.ourcraft.sharedcore.StdRegName;
import com.ksptool.ourcraft.sharedcore.world.chunk.SharedChunk;
import java.util.ArrayList;
import java.util.List;

/**
 * 地形管道类，负责按顺序执行多个地形层
 */
public class DefaultTerrainGenerator implements TerrainGenerator{

    @Override
    public StdRegName getStdRegName() {
        return StdRegName.of("ourcraft:terrain_generator:earth_like");
    }

    //地形层列表
    private final List<TerrainLayer> layers;

    public DefaultTerrainGenerator() {
        this.layers = new ArrayList<>();
    }

    public void addLayer(TerrainLayer layer) {
        if (layer == null) {
            return;
        }
        layers.add(layer);
    }

    //执行地形管道
    public void execute(SharedChunk chunk, GenerationContext context) {
        int[][][] chunkData = new int[chunk.getSizeX()][chunk.getSizeY()][chunk.getSizeZ()];
        
        for (TerrainLayer layer : layers) {
            layer.apply(chunkData, chunk.getX(), chunk.getZ(), context);
        }
        
        for (int x = 0; x < chunk.getSizeX(); x++) {
            for (int y = 0; y < chunk.getSizeY(); y++) {
                for (int z = 0; z < chunk.getSizeZ(); z++) {
                    chunk.setBlockState(x, y, z, chunkData[x][y][z]);
                }
            }
        }
    }

}

