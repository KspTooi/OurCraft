package com.ksptool.ourcraft.server.world.chunk;

import com.ksptool.ourcraft.sharedcore.BoundingBox;
import com.ksptool.ourcraft.sharedcore.GlobalPalette;
import com.ksptool.ourcraft.sharedcore.utils.position.ChunkPos;
import com.ksptool.ourcraft.sharedcore.world.BlockState;
import com.ksptool.ourcraft.sharedcore.world.SharedChunk;
import lombok.Getter;
import lombok.Setter;

/**
 * 服务端区块类，只负责存储和管理方块数据，不包含任何渲染相关代码
 */
public class ServerChunkOld implements SharedChunk {

    //区块大小
    public static final int CHUNK_SIZE = 16;
    
    //区块高度
    public static final int CHUNK_HEIGHT = 256;

    public enum ChunkState {
        NEW,
        DATA_LOADED,
        AWAITING_MESH,
        READY_TO_UPLOAD,
        READY
    }

    @Setter
    @Getter
    private int[][][] blockStates;
    @Getter
    private int chunkX;
    @Getter
    private int chunkZ;

    @Getter
    private final ChunkPos chunkPos;

    private boolean needsUpdate;
    @Setter
    @Getter
    private ChunkState state;
    @Getter
    private BoundingBox boundingBox;
    private static final int AIR_STATE_ID = 0;
    @Getter
    private boolean isDirty = false;
    private boolean entitiesDirty = false;

    public ServerChunkOld(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.blockStates = new int[CHUNK_SIZE][CHUNK_HEIGHT][CHUNK_SIZE];
        this.needsUpdate = true;
        this.state = ChunkState.NEW;
        
        float minX = chunkX * CHUNK_SIZE;
        float maxX = minX + CHUNK_SIZE;
        float minZ = chunkZ * CHUNK_SIZE;
        float maxZ = minZ + CHUNK_SIZE;
        this.boundingBox = new BoundingBox(minX, 0, minZ, maxX, CHUNK_HEIGHT, maxZ);
        this.chunkPos = ChunkPos.of(chunkX, chunkZ);
    }

    @Override
    public boolean isServerSide() {
        return true;
    }

    @Override
    public boolean isClientSide() {
        return false;
    }

    public void setBlockState(int x, int y, int z, int stateId) {
        if (x >= 0 && x < CHUNK_SIZE && y >= 0 && y < CHUNK_HEIGHT && z >= 0 && z < CHUNK_SIZE) {
            blockStates[x][y][z] = stateId;
            needsUpdate = true;
            markDirty(true);
        }
    }

    @Override
    public void setBlockState(int x, int y, int z, BlockState state) {

    }

    @Override
    public int getBlockStateId(int x, int y, int z) {
        if (x >= 0 && x < CHUNK_SIZE && y >= 0 && y < CHUNK_HEIGHT && z >= 0 && z < CHUNK_SIZE) {
            return blockStates[x][y][z];
        }
        return AIR_STATE_ID;
    }

    @Override
    public BlockState getBlockState(int x, int y, int z) {
        if (x >= 0 && x < CHUNK_SIZE && y >= 0 && y < CHUNK_HEIGHT && z >= 0 && z < CHUNK_SIZE) {
            return GlobalPalette.getInstance().getState(blockStates[x][y][z]);
        }
        return GlobalPalette.getInstance().getState(AIR_STATE_ID);
    }


    @Override
    public int getX() {
        return chunkX;
    }

    @Override
    public int getZ() {
        return chunkZ;
    }

    @Override
    public int getSizeX() {
        return CHUNK_SIZE;
    }

    @Override
    public int getSizeY() {
        return CHUNK_HEIGHT;
    }

    @Override
    public int getSizeZ() {
        return CHUNK_SIZE;
    }


    public void cleanup() {
    }

    public boolean needsUpdate() {
        return needsUpdate;
    }

    public void markDirty(boolean isDirty) {
        this.isDirty = isDirty;
    }

    public void markEntitiesDirty(boolean entitiesDirty) {
        this.entitiesDirty = entitiesDirty;
    }

    public boolean areEntitiesDirty() {
        return entitiesDirty;
    }

}

