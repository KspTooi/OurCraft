package com.ksptool.ourcraft.client.world;

import com.ksptool.ourcraft.client.entity.ClientPlayer;
import com.ksptool.ourcraft.client.rendering.Mesh;
import com.ksptool.ourcraft.sharedcore.utils.ChunkUtils;
import com.ksptool.ourcraft.sharedcore.utils.FlexChunkData;
import com.ksptool.ourcraft.sharedcore.utils.FlexChunkSerializer;
import com.ksptool.ourcraft.sharedcore.world.SharedWorld;
import com.ksptool.ourcraft.sharedcore.world.WorldTemplate;
import com.ksptool.ourcraft.sharedcore.network.nvo.PsChunkNVo;
import com.ksptool.ourcraft.sharedcore.network.nvo.HuChunkNVo;
import com.ksptool.ourcraft.sharedcore.network.nvo.HuChunkUnloadNVo;
import com.ksptool.ourcraft.sharedcore.network.packets.ServerSyncBlockUpdateNVo;
import com.ksptool.ourcraft.sharedcore.GlobalPalette;
import com.ksptool.ourcraft.sharedcore.world.BlockState;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

/**
 * 客户端世界类，负责渲染数据的缓存和管理
 * 使用FlexClientChunk架构，支持高效的压缩存储和线程安全的异步网格生成
 */
@Slf4j
@Getter
public class ClientWorld implements SharedWorld {

    private final Map<Long, FlexClientChunk> chunks = new ConcurrentHashMap<>();

    //世界模板
    private final WorldTemplate template;
    
    private float timeOfDay = 0.0f;

    //客户端本地玩家
    private ClientPlayer player;
    private FlexChunkMeshGenerator chunkMeshGenerator;
    private final List<FlexClientChunk> dirtyChunks = new ArrayList<>();
    
    public ClientWorld(WorldTemplate template) {
        this.template = template;
        this.chunkMeshGenerator = new FlexChunkMeshGenerator(this);
    }
    
    public void setPlayer(ClientPlayer player) {
        this.player = player;
    }
    
    /**
     * 处理PsChunkNVo数据包，反序列化并存储Flex区块数据
     */
    public void handleChunkData(PsChunkNVo packet) {
        if (packet == null) {
            log.warn("收到null的PsChunkNVo数据包");
            return;
        }
        
        int chunkX = packet.chunkX();
        int chunkZ = packet.chunkZ();
        byte[] blockData = packet.blockData();
        
        if (blockData == null || blockData.length == 0) {
            log.warn("收到空的区块数据: ({}, {})", chunkX, chunkZ);
            return;
        }
        
        try {
            // 反序列化FlexChunkData
            FlexChunkData flexChunkData = FlexChunkSerializer.deserialize(blockData);
            
            // 获取或创建FlexClientChunk
            FlexClientChunk chunk = getChunk(chunkX, chunkZ);
            if (chunk == null) {
                chunk = new FlexClientChunk(chunkX, chunkZ);
                putChunk(chunkX, chunkZ, chunk);
            }
            
            // 设置区块数据
            chunk.setFlexChunkData(flexChunkData);
            
            // 标记为需要网格更新
            synchronized (dirtyChunks) {
                if (!dirtyChunks.contains(chunk)) {
                    dirtyChunks.add(chunk);
                }
            }
            
            log.debug("处理区块数据: ({}, {}), 数据大小: {} bytes", chunkX, chunkZ, blockData.length);
        } catch (Exception e) {
            log.error("处理区块数据失败: ({}, {})", chunkX, chunkZ, e);
        }
    }
    
    /**
     * 处理热更新区块数据 (HuChunkNVo)
     */
    public void handleHotUpdateChunk(HuChunkNVo packet) {
        if (packet == null) {
            log.warn("收到null的HuChunkNVo数据包");
            return;
        }
        
        // 热更新区块的处理逻辑与PsChunkNVo相同
        int chunkX = packet.chunkX();
        int chunkZ = packet.chunkZ();
        byte[] blockData = packet.blockData();
        
        if (blockData == null || blockData.length == 0) {
            log.warn("收到空的热更新区块数据: ({}, {})", chunkX, chunkZ);
            return;
        }
        
        try {
            // 反序列化FlexChunkData
            FlexChunkData flexChunkData = FlexChunkSerializer.deserialize(blockData);
            
            // 获取或创建FlexClientChunk
            FlexClientChunk chunk = getChunk(chunkX, chunkZ);
            if (chunk == null) {
                chunk = new FlexClientChunk(chunkX, chunkZ);
                putChunk(chunkX, chunkZ, chunk);
            }
            
            // 设置区块数据
            chunk.setFlexChunkData(flexChunkData);
            
            // 标记为需要网格更新
            synchronized (dirtyChunks) {
                if (!dirtyChunks.contains(chunk)) {
                    dirtyChunks.add(chunk);
                }
            }
            
            log.debug("处理热更新区块数据: ({}, {}), 数据大小: {} bytes", chunkX, chunkZ, blockData.length);
        } catch (Exception e) {
            log.error("处理热更新区块数据失败: ({}, {})", chunkX, chunkZ, e);
        }
    }
    
    /**
     * 处理区块卸载 (HuChunkUnloadNVo)
     */
    public void handleChunkUnload(HuChunkUnloadNVo packet) {
        if (packet == null || packet.pos() == null) {
            log.warn("收到null的HuChunkUnloadNVo数据包");
            return;
        }
        
        int chunkX = packet.pos().getX();
        int chunkZ = packet.pos().getZ();
        removeChunk(chunkX, chunkZ);
        log.debug("卸载区块: ({}, {})", chunkX, chunkZ);
    }
    
    /**
     * 处理方块更新 (ServerSyncBlockUpdateNVo)
     */
    public void handleBlockUpdate(ServerSyncBlockUpdateNVo packet) {
        if (packet == null) {
            log.warn("收到null的ServerSyncBlockUpdateNVo数据包");
            return;
        }
        
        int worldX = packet.x();
        int worldY = packet.y();
        int worldZ = packet.z();
        int blockId = packet.blockId();
        
        // 计算区块坐标
        int chunkX = (int) Math.floor((float) worldX / FlexClientChunk.CHUNK_SIZE);
        int chunkZ = (int) Math.floor((float) worldZ / FlexClientChunk.CHUNK_SIZE);
        
        FlexClientChunk chunk = getChunk(chunkX, chunkZ);
        if (chunk == null) {
            log.warn("收到方块更新但区块不存在: ({}, {}, {})", worldX, worldY, worldZ);
            return;
        }
        
        // 计算区块内坐标
        int localX = worldX - chunkX * FlexClientChunk.CHUNK_SIZE;
        int localZ = worldZ - chunkZ * FlexClientChunk.CHUNK_SIZE;
        
        // 从GlobalPalette获取BlockState
        BlockState blockState = GlobalPalette.getInstance().getState(blockId);
        
        // 设置方块状态
        chunk.setBlockState(localX, worldY, localZ, blockState);
        
        // 标记为需要网格更新
        markChunkForMeshUpdate(chunkX, chunkZ);
        
        log.debug("收到方块更新: ({}, {}, {}) -> blockId={}", worldX, worldY, worldZ, blockId);
    }
    
    /**
     * 添加或更新客户端区块
     */
    public void putChunk(int chunkX, int chunkZ, FlexClientChunk chunk) {
        long key = ChunkUtils.getChunkKey(chunkX, chunkZ);
        chunks.put(key, chunk);
    }
    
    /**
     * 获取客户端区块
     */
    public FlexClientChunk getChunk(int chunkX, int chunkZ) {
        long key = ChunkUtils.getChunkKey(chunkX, chunkZ);
        return chunks.get(key);
    }
    
    /**
     * 移除客户端区块
     */
    public void removeChunk(int chunkX, int chunkZ) {
        long key = ChunkUtils.getChunkKey(chunkX, chunkZ);
        FlexClientChunk chunk = chunks.remove(key);
        if (chunk != null) {
            chunk.cleanup();
        }
    }
    
    /**
     * 将区块标记为需要网格更新（用于网络接收的区块数据）
     */
    public void markChunkForMeshUpdate(int chunkX, int chunkZ) {
        FlexClientChunk chunk = getChunk(chunkX, chunkZ);
        if (chunk != null) {
            chunk.markNeedsMeshUpdate();
            synchronized (dirtyChunks) {
                if (!dirtyChunks.contains(chunk)) {
                    dirtyChunks.add(chunk);
                }
            }
        }
    }
    
    public void setTimeOfDay(float timeOfDay) {
        this.timeOfDay = timeOfDay;
    }

    public float getTimeOfDay() {
        return timeOfDay;
    }
    
    /**
     * 获取指定坐标的方块状态ID（用于网格生成和物理检测）
     */
    public int getBlockState(int x, int y, int z) {
        int chunkX = (int) Math.floor((float) x / FlexClientChunk.CHUNK_SIZE);
        int chunkZ = (int) Math.floor((float) z / FlexClientChunk.CHUNK_SIZE);
        FlexClientChunk chunk = getChunk(chunkX, chunkZ);
        if (chunk == null) {
            return 0;
        }
        int localX = x - chunkX * FlexClientChunk.CHUNK_SIZE;
        int localZ = z - chunkZ * FlexClientChunk.CHUNK_SIZE;
        return chunk.getBlockStateId(localX, y, localZ);
    }
    
    public void processMeshGeneration() {
        if (chunkMeshGenerator == null) {
            return;
        }
        
        List<Future<MeshGenerationResult>> futures = chunkMeshGenerator.getPendingFutures();
        List<Future<MeshGenerationResult>> completedFutures = new ArrayList<>();
        
        for (Future<MeshGenerationResult> future : futures) {
            if (future.isDone()) {
                try {
                    MeshGenerationResult result = future.get();
                    if (result != null) {
                        FlexClientChunk chunk = getChunk(result.chunkX, result.chunkZ);
                        if (chunk != null) {
                            if (result.vertices.length > 0) {
                                Mesh mesh = new Mesh(
                                    result.vertices,
                                    result.texCoords,
                                    result.tints,
                                    result.animationData,
                                    result.indices
                                );
                                chunk.setMesh(mesh);
                            }
                            
                            if (result.transparentVertices.length > 0) {
                                Mesh transparentMesh = new Mesh(
                                    result.transparentVertices,
                                    result.transparentTexCoords,
                                    result.transparentTints,
                                    result.transparentAnimationData,
                                    result.transparentIndices
                                );
                                chunk.setTransparentMesh(transparentMesh);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("处理网格生成结果失败", e);
                }
                completedFutures.add(future);
            }
        }
        
        futures.removeAll(completedFutures);
        
        synchronized (dirtyChunks) {
            for (FlexClientChunk chunk : dirtyChunks) {
                if (chunk.needsMeshUpdate()) {
                    chunkMeshGenerator.submitMeshTask(chunk);
                }
            }
            dirtyChunks.clear();
        }
    }
    
    public void cleanup() {
        if (chunkMeshGenerator != null) {
            chunkMeshGenerator.shutdown();
        }
        for (FlexClientChunk chunk : chunks.values()) {
            chunk.cleanup();
        }
        chunks.clear();
    }

    @Override
    public boolean isServerSide() {
        return false;
    }

    @Override
    public boolean isClientSide() {
        return true;
    }

    @Override
    public void action(double delta) {
        // 客户端世界不需要action循环，由主循环驱动
    }
}
