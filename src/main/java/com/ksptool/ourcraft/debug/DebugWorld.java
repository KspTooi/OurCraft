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

    /**
     * 获取世界坐标对应的方块状态
     */
    public int getBlockState(int worldX, int worldY, int worldZ) {
        DebugChunk chunk = findChunkForWorldPos(worldX, worldZ);
        if (chunk == null) {
            return 0;
        }

        int localX = worldX - chunk.getChunkX() * chunk.getSizeX();
        int localZ = worldZ - chunk.getChunkZ() * chunk.getSizeZ();

        if (localX < 0) {
            localX += chunk.getSizeX();
        }
        if (localZ < 0) {
            localZ += chunk.getSizeZ();
        }

        return chunk.getBlockState(localX, worldY, localZ);
    }

    /**
     * 获取指定坐标的最高非空方块Y坐标
     */
    public int getTopBlockY(int worldX, int worldZ) {
        DebugChunk chunk = findChunkForWorldPos(worldX, worldZ);
        if (chunk == null) {
            return 0;
        }

        int localX = worldX - chunk.getChunkX() * chunk.getSizeX();
        int localZ = worldZ - chunk.getChunkZ() * chunk.getSizeZ();

        if (localX < 0) {
            localX += chunk.getSizeX();
        }
        if (localZ < 0) {
            localZ += chunk.getSizeZ();
        }

        for (int y = chunk.getSizeY() - 1; y >= 0; y--) {
            int stateId = chunk.getBlockState(localX, y, localZ);
            if (stateId != 0) {
                return y;
            }
        }
        return 0;
    }

    DebugChunk findChunkForWorldPos(int worldX, int worldZ) {
        for (DebugChunk chunk : chunks.values()) {
            int chunkWorldXStart = chunk.getChunkX() * chunk.getSizeX();
            int chunkWorldXEnd = chunkWorldXStart + chunk.getSizeX();
            int chunkWorldZStart = chunk.getChunkZ() * chunk.getSizeZ();
            int chunkWorldZEnd = chunkWorldZStart + chunk.getSizeZ();

            if (worldX >= chunkWorldXStart && worldX < chunkWorldXEnd &&
                worldZ >= chunkWorldZStart && worldZ < chunkWorldZEnd) {
                return chunk;
            }
        }
        return null;
    }
}
