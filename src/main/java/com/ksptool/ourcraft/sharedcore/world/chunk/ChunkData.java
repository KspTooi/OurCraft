package com.ksptool.ourcraft.sharedcore.world.chunk;

import lombok.Getter;

/*
 * 存储
 */
public class ChunkData {

    private int[][][] blockStates;

    @Getter
    private final int chunkSize;
    
    @Getter
    private final int chunkHeight;

    public ChunkData(int chunkSize, int chunkHeight){
        this.chunkSize = chunkSize;
        this.chunkHeight = chunkHeight;
        this.blockStates = new int[chunkSize][chunkHeight][chunkSize];
    }

    


}
