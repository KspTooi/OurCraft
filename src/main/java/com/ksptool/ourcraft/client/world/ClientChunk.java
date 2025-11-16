package com.ksptool.ourcraft.client.world;

import com.ksptool.ourcraft.client.rendering.Mesh;
import com.ksptool.ourcraft.sharedcore.BoundingBox;
import lombok.Getter;

/**
 * 客户端区块，存储渲染相关的数据
 */
@Getter
public class ClientChunk {
    //区块大小
    public static final int CHUNK_SIZE = 16;
    
    //区块高度
    public static final int CHUNK_HEIGHT = 256;
    
    private final int chunkX;
    private final int chunkZ;
    private int[][][] blockStates;
    private Mesh mesh;
    private Mesh transparentMesh;
    private BoundingBox boundingBox;
    private boolean needsMeshUpdate = false;
    
    public ClientChunk(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.blockStates = new int[CHUNK_SIZE][CHUNK_HEIGHT][CHUNK_SIZE];
        
        float minX = chunkX * CHUNK_SIZE;
        float maxX = minX + CHUNK_SIZE;
        float minZ = chunkZ * CHUNK_SIZE;
        float maxZ = minZ + CHUNK_SIZE;
        this.boundingBox = new BoundingBox(minX, 0, minZ, maxX, CHUNK_HEIGHT, maxZ);
    }
    
    public void setBlockStates(int[][][] blockStates) {
        this.blockStates = blockStates;
        this.needsMeshUpdate = true;
    }
    
    public int getBlockState(int x, int y, int z) {
        if (x >= 0 && x < CHUNK_SIZE && y >= 0 && y < CHUNK_HEIGHT && z >= 0 && z < CHUNK_SIZE) {
            return blockStates[x][y][z];
        }
        return 0;
    }
    
    public void setBlockState(int x, int y, int z, int stateId) {
        if (x >= 0 && x < CHUNK_SIZE && y >= 0 && y < CHUNK_HEIGHT && z >= 0 && z < CHUNK_SIZE) {
            blockStates[x][y][z] = stateId;
            needsMeshUpdate = true;
        }
    }
    
    public void setMesh(Mesh mesh) {
        if (this.mesh != null) {
            this.mesh.cleanup();
        }
        this.mesh = mesh;
        this.needsMeshUpdate = false;
    }
    
    public void setTransparentMesh(Mesh transparentMesh) {
        if (this.transparentMesh != null) {
            this.transparentMesh.cleanup();
        }
        this.transparentMesh = transparentMesh;
    }
    
    public boolean hasMesh() {
        return mesh != null;
    }
    
    public boolean hasTransparentMesh() {
        return transparentMesh != null;
    }
    
    public void markNeedsMeshUpdate() {
        this.needsMeshUpdate = true;
    }
    
    public boolean needsMeshUpdate() {
        return needsMeshUpdate;
    }
    
    public void render() {
        if (mesh != null) {
            mesh.render();
        }
    }
    
    public void renderTransparent() {
        if (transparentMesh != null) {
            transparentMesh.render();
        }
    }
    
    public void cleanup() {
        if (mesh != null) {
            mesh.cleanup();
            mesh = null;
        }
        if (transparentMesh != null) {
            transparentMesh.cleanup();
            transparentMesh = null;
        }
    }
}

