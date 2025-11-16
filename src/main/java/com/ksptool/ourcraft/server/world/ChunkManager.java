package com.ksptool.ourcraft.server.world;

import com.ksptool.ourcraft.server.world.ServerChunk;
import com.ksptool.ourcraft.server.world.ServerWorld;
import com.ksptool.ourcraft.server.world.save.ChunkSerializer;
import com.ksptool.ourcraft.server.world.save.RegionFile;
import com.ksptool.ourcraft.server.world.save.RegionManager;
import com.ksptool.ourcraft.sharedcore.world.ChunkUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.joml.Vector3f;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 区块管理器，负责区块的加载、卸载、缓存和存盘
 */
@Slf4j
@Getter
public class ChunkManager {
    private static final int RENDER_DISTANCE = 8;
    
    private final ServerWorld world;
    private Map<Long, ServerChunk> chunks;
    private final BlockingQueue<ChunkGenerationTask> generationQueue;
    private final Map<Long, ChunkGenerationTask> pendingChunks;
    private WorldGenerator worldGenerator;
    
    private int lastPlayerChunkX = Integer.MIN_VALUE;
    private int lastPlayerChunkZ = Integer.MIN_VALUE;
    
    private RegionManager regionManager;
    @Setter
    private String saveName;
    
    public static long getChunkKey(int x, int z) {
        return ChunkUtils.getChunkKey(x, z);
    }
    
    public ChunkManager(ServerWorld world) {
        this.world = world;
        this.chunks = new ConcurrentHashMap<>();
        this.generationQueue = new LinkedBlockingQueue<>();
        this.pendingChunks = new ConcurrentHashMap<>();
    }
    
    public void init() {
        worldGenerator = new WorldGenerator(world, generationQueue);
        worldGenerator.start();
    }
    
    public void setRegionManager(RegionManager regionManager) {
        this.regionManager = regionManager;
    }
    
    public void update(Vector3f playerPosition) {
        int playerChunkX = (int) Math.floor(playerPosition.x / ServerChunk.CHUNK_SIZE);
        int playerChunkZ = (int) Math.floor(playerPosition.z / ServerChunk.CHUNK_SIZE);

        if (playerChunkX != lastPlayerChunkX || playerChunkZ != lastPlayerChunkZ) {
            for (int x = playerChunkX - RENDER_DISTANCE; x <= playerChunkX + RENDER_DISTANCE; x++) {
                for (int z = playerChunkZ - RENDER_DISTANCE; z <= playerChunkZ + RENDER_DISTANCE; z++) {
                    long key = getChunkKey(x, z);
                    if (!chunks.containsKey(key) && !pendingChunks.containsKey(key)) {
                        ChunkGenerationTask task = new ChunkGenerationTask(x, z);
                        pendingChunks.put(key, task);
                        generationQueue.offer(task);
                    }
                }
            }

            for (int x = playerChunkX - RENDER_DISTANCE; x <= playerChunkX + RENDER_DISTANCE; x++) {
                for (int z = playerChunkZ - RENDER_DISTANCE; z <= playerChunkZ + RENDER_DISTANCE; z++) {
                    long key = getChunkKey(x, z);
                    ChunkGenerationTask task = pendingChunks.get(key);
                    if (task != null && task.isDataGenerated() && task.getChunk() != null) {
                        ServerChunk chunk = task.getChunk();
                        if (!chunks.containsKey(key)) {
                            chunks.put(key, chunk);
                        }
                        if (chunk.getState() == ServerChunk.ChunkState.DATA_LOADED) {
                            chunk.setState(ServerChunk.ChunkState.READY);
                        }
                    }
                }
            }
            
            chunks.entrySet().removeIf(entry -> {
                long key = entry.getKey();
                ServerChunk chunk = entry.getValue();
                int chunkX = chunk.getChunkX();
                int chunkZ = chunk.getChunkZ();
                int distance = Math.max(Math.abs(chunkX - playerChunkX), Math.abs(chunkZ - playerChunkZ));
                if (distance > RENDER_DISTANCE + 5) {
                    if (chunk.isDirty() && regionManager != null && StringUtils.isNotBlank(saveName)) {
                        int regionX = RegionManager.getRegionX(chunkX);
                        int regionZ = RegionManager.getRegionZ(chunkZ);
                        int localX = RegionManager.getLocalChunkX(chunkX);
                        int localZ = RegionManager.getLocalChunkZ(chunkZ);
                        try {
                            log.debug("卸载时保存脏区块 [{},{}]", chunkX, chunkZ);
                            byte[] compressedData = ChunkSerializer.serialize(chunk);
                            RegionFile regionFile = regionManager.getRegionFile(regionX, regionZ);
                            regionFile.open();
                            regionFile.writeChunk(localX, localZ, compressedData);
                            chunk.markDirty(false);
                        } catch (Exception e) {
                            log.error("卸载时保存区块失败 [{},{}]", chunkX, chunkZ, e);
                        }
                    }
                    if (chunk.areEntitiesDirty() && world.getEntityManager().getEntityRegionManager() != null && StringUtils.isNotBlank(saveName)) {
                        log.debug("卸载时保存脏实体区块 [{},{}]", chunkX, chunkZ);
                        world.getEntityManager().saveEntitiesForChunk(chunkX, chunkZ);
                        chunk.markEntitiesDirty(false);
                    }
                    chunk.cleanup();
                    pendingChunks.remove(key);
                    return true;
                }
                return false;
            });

            lastPlayerChunkX = playerChunkX;
            lastPlayerChunkZ = playerChunkZ;
        }
    }
    
    public ServerChunk getChunk(int chunkX, int chunkZ) {
        long key = getChunkKey(chunkX, chunkZ);
        ServerChunk chunk = chunks.get(key);
        
        if (chunk != null) {
            return chunk;
        }
        
        if (regionManager != null && StringUtils.isNotBlank(saveName)) {
            chunk = loadChunkFromRegion(chunkX, chunkZ);
            if (chunk != null) {
                chunks.put(key, chunk);
                return chunk;
            }
        }
        
        return null;
    }
    
    private ServerChunk loadChunkFromRegion(int chunkX, int chunkZ) {
        if (regionManager == null) {
            log.debug("无法加载区块 [{},{}]: 区域管理器未初始化", chunkX, chunkZ);
            return null;
        }
        
        try {
            int regionX = RegionManager.getRegionX(chunkX);
            int regionZ = RegionManager.getRegionZ(chunkZ);
            int localX = RegionManager.getLocalChunkX(chunkX);
            int localZ = RegionManager.getLocalChunkZ(chunkZ);
            
            log.debug("动态加载区块 [{},{}] 从区域 [{},{}] 本地坐标 [{},{}]", chunkX, chunkZ, regionX, regionZ, localX, localZ);
            
            RegionFile regionFile = regionManager.getRegionFile(regionX, regionZ);
            regionFile.open();
            
            byte[] compressedData = regionFile.readChunk(localX, localZ);
            if (compressedData == null) {
                log.debug("区块 [{},{}] 不存在于区域文件中", chunkX, chunkZ);
                return null;
            }
            
            ServerChunk chunk = ChunkSerializer.deserialize(compressedData, chunkX, chunkZ);
            
            if (chunk == null) {
                log.warn("区块 [{},{}] 反序列化失败", chunkX, chunkZ);
                return null;
            }
            
            log.debug("成功加载区块 [{},{}]", chunkX, chunkZ);
            if (world.getEntityManager().getEntityRegionManager() != null) {
                world.getEntityManager().loadEntitiesForChunk(chunkX, chunkZ);
            }
            
            return chunk;
        } catch (Exception e) {
            log.error("加载区块失败 [{},{}]", chunkX, chunkZ, e);
            return null;
        }
    }
    
    public int getBlockState(int x, int y, int z) {
        int chunkX = (int) Math.floor((float) x / ServerChunk.CHUNK_SIZE);
        int chunkZ = (int) Math.floor((float) z / ServerChunk.CHUNK_SIZE);
        long key = getChunkKey(chunkX, chunkZ);
        ServerChunk chunk = chunks.get(key);
        if (chunk == null) {
            return 0;
        }
        int localX = x - chunkX * ServerChunk.CHUNK_SIZE;
        int localZ = z - chunkZ * ServerChunk.CHUNK_SIZE;
        return chunk.getBlockState(localX, y, localZ);
    }
    
    public void setBlockState(int x, int y, int z, int stateId) {
        int chunkX = (int) Math.floor((float) x / ServerChunk.CHUNK_SIZE);
        int chunkZ = (int) Math.floor((float) z / ServerChunk.CHUNK_SIZE);
        long key = getChunkKey(chunkX, chunkZ);
        ServerChunk chunk = chunks.get(key);
        if (chunk == null) {
            chunk = getChunk(chunkX, chunkZ);
            if (chunk == null) {
                return;
            }
        }
        int localX = x - chunkX * ServerChunk.CHUNK_SIZE;
        int localZ = z - chunkZ * ServerChunk.CHUNK_SIZE;
        chunk.setBlockState(localX, y, localZ, stateId);
        if (chunk.getState() == ServerChunk.ChunkState.READY) {
            chunk.setState(ServerChunk.ChunkState.DATA_LOADED);
        }
    }
    
    public void generateChunkSynchronously(int chunkX, int chunkZ) {
        ServerChunk chunk = getChunk(chunkX, chunkZ);

        if (chunk == null) {
            long key = getChunkKey(chunkX, chunkZ);
            chunk = new ServerChunk(chunkX, chunkZ);
            world.generateChunkData(chunk);
            chunks.put(key, chunk);
        }

        if (chunk.getState() == ServerChunk.ChunkState.DATA_LOADED || chunk.getState() == ServerChunk.ChunkState.NEW) {
            chunk.setState(ServerChunk.ChunkState.READY);
        }
    }
    
    public int getChunkCount() {
        return chunks.size();
    }
    
    
    public void saveAllDirtyChunks() {
        if (regionManager == null || StringUtils.isBlank(saveName)) {
            log.debug("跳过保存: 区域管理器未初始化或存档名称为空");
            return;
        }
        
        try {
            int dirtyChunkCount = 0;
            
            for (Map.Entry<Long, ServerChunk> entry : chunks.entrySet()) {
                ServerChunk chunk = entry.getValue();
                if (chunk == null) {
                    continue;
                }
                
                int chunkX = chunk.getChunkX();
                int chunkZ = chunk.getChunkZ();
                
                if (chunk.isDirty()) {
                    int regionX = RegionManager.getRegionX(chunkX);
                    int regionZ = RegionManager.getRegionZ(chunkZ);
                    int localX = RegionManager.getLocalChunkX(chunkX);
                    int localZ = RegionManager.getLocalChunkZ(chunkZ);
                    
                    log.debug("保存脏区块 [{},{}] 到区域 [{},{}] 本地坐标 [{},{}]", chunkX, chunkZ, regionX, regionZ, localX, localZ);
                    
                    byte[] compressedData = ChunkSerializer.serialize(chunk);
                    
                    RegionFile regionFile = regionManager.getRegionFile(regionX, regionZ);
                    regionFile.open();
                    regionFile.writeChunk(localX, localZ, compressedData);
                    
                    chunk.markDirty(false);
                    dirtyChunkCount++;
                }
            }
            
            if (dirtyChunkCount == 0) {
                log.debug("没有需要保存的脏区块");
                return;
            }
            log.info("保存完成: 脏区块数={}", dirtyChunkCount);
        } catch (Exception e) {
            log.error("保存区块失败", e);
        }
    }
    
    public void cleanup() {
        if (worldGenerator != null) {
            worldGenerator.stopGenerator();
            try {
                worldGenerator.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        for (ServerChunk chunk : chunks.values()) {
            chunk.cleanup();
        }
        chunks.clear();
        pendingChunks.clear();
        generationQueue.clear();
    }
}

