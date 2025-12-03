package com.ksptool.ourcraft.debug;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 调试客户端世界数据存储
 */
public class DebugWorld {
    
    private final Map<Long, DebugChunk> chunks = new ConcurrentHashMap<>();
    
    private static long getChunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }
    
    public DebugChunk getChunk(int chunkX, int chunkZ) {
        return chunks.get(getChunkKey(chunkX, chunkZ));
    }
    
    public void putChunk(int chunkX, int chunkZ, DebugChunk chunk) {
        chunks.put(getChunkKey(chunkX, chunkZ), chunk);
    }
    
    public void removeChunk(int chunkX, int chunkZ) {
        chunks.remove(getChunkKey(chunkX, chunkZ));
    }
    
    public int getBlockState(int worldX, int worldY, int worldZ) {
        int chunkX = worldX >= 0 ? worldX / DebugChunk.CHUNK_SIZE : (worldX + 1) / DebugChunk.CHUNK_SIZE - 1;
        int chunkZ = worldZ >= 0 ? worldZ / DebugChunk.CHUNK_SIZE : (worldZ + 1) / DebugChunk.CHUNK_SIZE - 1;
        
        int localX = worldX - chunkX * DebugChunk.CHUNK_SIZE;
        int localZ = worldZ - chunkZ * DebugChunk.CHUNK_SIZE;
        
        if (localX < 0) {
            localX += DebugChunk.CHUNK_SIZE;
        }
        if (localZ < 0) {
            localZ += DebugChunk.CHUNK_SIZE;
        }
        
        DebugChunk chunk = getChunk(chunkX, chunkZ);
        if (chunk == null) {
            return 0;
        }
        
        return chunk.getBlockState(localX, worldY, localZ);
    }
    
    public int getTopBlockY(int worldX, int worldZ) {
        int chunkX = worldX >= 0 ? worldX / DebugChunk.CHUNK_SIZE : (worldX + 1) / DebugChunk.CHUNK_SIZE - 1;
        int chunkZ = worldZ >= 0 ? worldZ / DebugChunk.CHUNK_SIZE : (worldZ + 1) / DebugChunk.CHUNK_SIZE - 1;
        
        int localX = worldX - chunkX * DebugChunk.CHUNK_SIZE;
        int localZ = worldZ - chunkZ * DebugChunk.CHUNK_SIZE;
        
        if (localX < 0) {
            localX += DebugChunk.CHUNK_SIZE;
        }
        if (localZ < 0) {
            localZ += DebugChunk.CHUNK_SIZE;
        }
        
        DebugChunk chunk = getChunk(chunkX, chunkZ);
        if (chunk == null) {
            return 0;
        }
        
        for (int y = DebugChunk.CHUNK_HEIGHT - 1; y >= 0; y--) {
            int stateId = chunk.getBlockState(localX, y, localZ);
            if (stateId != 0) {
                return y;
            }
        }
        return 0;
    }
}

