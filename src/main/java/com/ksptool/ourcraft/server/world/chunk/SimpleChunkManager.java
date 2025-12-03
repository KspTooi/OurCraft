package com.ksptool.ourcraft.server.world.chunk;

import com.ksptool.ourcraft.server.world.ServerWorld;
import com.ksptool.ourcraft.server.world.gen.ChunkGenerationTask;
import com.ksptool.ourcraft.server.world.save.RegionFile;
import com.ksptool.ourcraft.server.world.save.SimpleRegionManager;
import com.ksptool.ourcraft.sharedcore.utils.ChunkUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

/**
 * 区块管理器，负责区块的加载、卸载、缓存和存盘
 */
@Slf4j
@Getter
public class SimpleChunkManager {
    private static final int RENDER_DISTANCE = 8;
    
    private final ServerWorld world;
    private Map<Long, SimpleServerChunk> chunks;
    private final BlockingQueue<ChunkGenerationTask> generationQueue;
    private final Map<Long, ChunkGenerationTask> pendingChunks;
    private SimpleWorldGeneratorThread simpleWorldGeneratorThread;
    
    private int lastPlayerChunkX = Integer.MIN_VALUE;
    private int lastPlayerChunkZ = Integer.MIN_VALUE;
    
    private SimpleRegionManager simpleRegionManager;

    @Setter
    private String saveName;
    
    public static long getChunkKey(int x, int z) {
        return ChunkUtils.getChunkKey(x, z);
    }
    
    public SimpleChunkManager(ServerWorld world) {
        this.world = world;
        this.chunks = new ConcurrentHashMap<>();
        this.generationQueue = new LinkedBlockingQueue<>();
        this.pendingChunks = new ConcurrentHashMap<>();
    }
    
    public void init() {
        simpleWorldGeneratorThread = new SimpleWorldGeneratorThread(world, generationQueue);
        simpleWorldGeneratorThread.start();
    }
    
    public void setSimpleRegionManager(SimpleRegionManager simpleRegionManager) {
        this.simpleRegionManager = simpleRegionManager;
    }

    /**
     * 更新区块管理器
     * @param playerPosition 玩家位置，用于计算玩家周围的区块
     */
    public void update(Vector3f playerPosition) {
        int playerChunkX = (int) Math.floor(playerPosition.x / SimpleServerChunk.CHUNK_SIZE);
        int playerChunkZ = (int) Math.floor(playerPosition.z / SimpleServerChunk.CHUNK_SIZE);

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
                        SimpleServerChunk chunk = task.getChunk();
                        if (!chunks.containsKey(key)) {
                            chunks.put(key, chunk);
                        }
                        if (chunk.getState() == SimpleServerChunk.ChunkState.DATA_LOADED) {
                            chunk.setState(SimpleServerChunk.ChunkState.READY);
                        }
                    }
                }
            }
            
            chunks.entrySet().removeIf(entry -> {
                long key = entry.getKey();
                SimpleServerChunk chunk = entry.getValue();
                int chunkX = chunk.getChunkX();
                int chunkZ = chunk.getChunkZ();
                int distance = Math.max(Math.abs(chunkX - playerChunkX), Math.abs(chunkZ - playerChunkZ));
                if (distance > RENDER_DISTANCE + 5) {
                    if (chunk.isDirty() && simpleRegionManager != null && StringUtils.isNotBlank(saveName)) {
                        int regionX = SimpleRegionManager.getRegionX(chunkX);
                        int regionZ = SimpleRegionManager.getRegionZ(chunkZ);
                        int localX = SimpleRegionManager.getLocalChunkX(chunkX);
                        int localZ = SimpleRegionManager.getLocalChunkZ(chunkZ);
                        try {
                            log.debug("卸载时保存脏区块 [{},{}]", chunkX, chunkZ);
                            byte[] compressedData = SimpleChunkSerializer.serialize(chunk);
                            RegionFile regionFile = simpleRegionManager.getRegionFile(regionX, regionZ);
                            regionFile.open();
                            regionFile.writeChunk(localX, localZ, compressedData);
                            chunk.markDirty(false);
                        } catch (Exception e) {
                            log.error("卸载时保存区块失败 [{},{}]", chunkX, chunkZ, e);
                        }
                    }
                    if (chunk.areEntitiesDirty() && world.getSes().getEntityRegionManager() != null && StringUtils.isNotBlank(saveName)) {
                        log.debug("卸载时保存脏实体区块 [{},{}]", chunkX, chunkZ);
                        world.getSes().saveEntitiesForChunk(chunkX, chunkZ);
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
    
    public SimpleServerChunk getChunk(int chunkX, int chunkZ) {
        long key = getChunkKey(chunkX, chunkZ);
        SimpleServerChunk chunk = chunks.get(key);
        
        if (chunk != null) {
            return chunk;
        }
        
        if (simpleRegionManager != null && StringUtils.isNotBlank(saveName)) {
            chunk = loadChunkFromRegion(chunkX, chunkZ);
            if (chunk != null) {
                chunks.put(key, chunk);
                return chunk;
            }
        }
        
        return null;
    }
    
    private SimpleServerChunk loadChunkFromRegion(int chunkX, int chunkZ) {
        if (simpleRegionManager == null) {
            log.debug("无法加载区块 [{},{}]: 区域管理器未初始化", chunkX, chunkZ);
            return null;
        }
        
        try {
            int regionX = SimpleRegionManager.getRegionX(chunkX);
            int regionZ = SimpleRegionManager.getRegionZ(chunkZ);
            int localX = SimpleRegionManager.getLocalChunkX(chunkX);
            int localZ = SimpleRegionManager.getLocalChunkZ(chunkZ);
            
            log.debug("动态加载区块 [{},{}] 从区域 [{},{}] 本地坐标 [{},{}]", chunkX, chunkZ, regionX, regionZ, localX, localZ);
            
            RegionFile regionFile = simpleRegionManager.getRegionFile(regionX, regionZ);
            regionFile.open();
            
            byte[] compressedData = regionFile.readChunk(localX, localZ);
            if (compressedData == null) {
                log.debug("区块 [{},{}] 不存在于区域文件中", chunkX, chunkZ);
                return null;
            }
            
            SimpleServerChunk chunk = SimpleChunkSerializer.deserialize(compressedData, chunkX, chunkZ);
            
            if (chunk == null) {
                log.warn("区块 [{},{}] 反序列化失败", chunkX, chunkZ);
                return null;
            }
            
            log.debug("成功加载区块 [{},{}]", chunkX, chunkZ);
            if (world.getSes().getEntityRegionManager() != null) {
                world.getSes().loadEntitiesForChunk(chunkX, chunkZ);
            }
            
            return chunk;
        } catch (Exception e) {
            log.error("加载区块失败 [{},{}]", chunkX, chunkZ, e);
            return null;
        }
    }
    
    public int getBlockState(int x, int y, int z) {
        int chunkX = (int) Math.floor((float) x / SimpleServerChunk.CHUNK_SIZE);
        int chunkZ = (int) Math.floor((float) z / SimpleServerChunk.CHUNK_SIZE);
        long key = getChunkKey(chunkX, chunkZ);
        SimpleServerChunk chunk = chunks.get(key);
        if (chunk == null) {
            return 0;
        }
        int localX = x - chunkX * SimpleServerChunk.CHUNK_SIZE;
        int localZ = z - chunkZ * SimpleServerChunk.CHUNK_SIZE;
        return chunk.getBlockStateId(localX, y, localZ);
    }
    
    public void setBlockState(int x, int y, int z, int stateId) {
        int chunkX = (int) Math.floor((float) x / SimpleServerChunk.CHUNK_SIZE);
        int chunkZ = (int) Math.floor((float) z / SimpleServerChunk.CHUNK_SIZE);
        long key = getChunkKey(chunkX, chunkZ);
        SimpleServerChunk chunk = chunks.get(key);
        if (chunk == null) {
            chunk = getChunk(chunkX, chunkZ);
            if (chunk == null) {
                return;
            }
        }
        int localX = x - chunkX * SimpleServerChunk.CHUNK_SIZE;
        int localZ = z - chunkZ * SimpleServerChunk.CHUNK_SIZE;
        chunk.setBlockState(localX, y, localZ, stateId);
        if (chunk.getState() == SimpleServerChunk.ChunkState.READY) {
            chunk.setState(SimpleServerChunk.ChunkState.DATA_LOADED);
        }
    }
    
    public void generateChunkSynchronously(int chunkX, int chunkZ) {
        SimpleServerChunk chunk = getChunk(chunkX, chunkZ);

        if (chunk == null) {
            long key = getChunkKey(chunkX, chunkZ);
            chunk = new SimpleServerChunk(chunkX, chunkZ);
            world.generateChunkData(chunk);
            chunks.put(key, chunk);
        }

        if (chunk.getState() == SimpleServerChunk.ChunkState.DATA_LOADED || chunk.getState() == SimpleServerChunk.ChunkState.NEW) {
            chunk.setState(SimpleServerChunk.ChunkState.READY);
        }
    }
    
    public int getChunkCount() {
        return chunks.size();
    }
    
    
    public void saveAllDirtyChunks() {
        if (simpleRegionManager == null || StringUtils.isBlank(saveName)) {
            log.debug("跳过保存: 区域管理器未初始化或存档名称为空");
            return;
        }
        
        try {
            int dirtyChunkCount = 0;
            
            for (Map.Entry<Long, SimpleServerChunk> entry : chunks.entrySet()) {
                SimpleServerChunk chunk = entry.getValue();
                if (chunk == null) {
                    continue;
                }
                
                int chunkX = chunk.getChunkX();
                int chunkZ = chunk.getChunkZ();
                
                if (chunk.isDirty()) {
                    int regionX = SimpleRegionManager.getRegionX(chunkX);
                    int regionZ = SimpleRegionManager.getRegionZ(chunkZ);
                    int localX = SimpleRegionManager.getLocalChunkX(chunkX);
                    int localZ = SimpleRegionManager.getLocalChunkZ(chunkZ);
                    
                    log.debug("保存脏区块 [{},{}] 到区域 [{},{}] 本地坐标 [{},{}]", chunkX, chunkZ, regionX, regionZ, localX, localZ);
                    
                    byte[] compressedData = SimpleChunkSerializer.serialize(chunk);
                    
                    RegionFile regionFile = simpleRegionManager.getRegionFile(regionX, regionZ);
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



    /**
     * 获取脏区块快照
     * @return 所有脏区块的快照数据
     */
    public List<SimpleServerChunk> getDirtyChunkSnapshot() {
        return chunks.values().stream()
            .filter(SimpleServerChunk::isDirty)
            .collect(Collectors.toList());
    }

    public void cleanup() {
        if (simpleWorldGeneratorThread != null) {
            simpleWorldGeneratorThread.stopGenerator();
            try {
                simpleWorldGeneratorThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        for (SimpleServerChunk chunk : chunks.values()) {
            chunk.cleanup();
        }
        chunks.clear();
        pendingChunks.clear();
        generationQueue.clear();
    }
}

