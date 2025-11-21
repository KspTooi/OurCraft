package com.ksptool.ourcraft.client.world;

import com.ksptool.ourcraft.client.entity.ClientPlayer;
import com.ksptool.ourcraft.client.rendering.Mesh;
import com.ksptool.ourcraft.sharedcore.events.BlockUpdateEvent;
import com.ksptool.ourcraft.sharedcore.events.ChunkDataEvent;
import com.ksptool.ourcraft.sharedcore.events.ChunkUnloadEvent;
import com.ksptool.ourcraft.sharedcore.events.ChunkUpdateEvent;
import com.ksptool.ourcraft.sharedcore.events.EventQueue;
import com.ksptool.ourcraft.sharedcore.events.GameEvent;
import com.ksptool.ourcraft.sharedcore.events.PlayerUpdateEvent;
import com.ksptool.ourcraft.sharedcore.events.TimeUpdateEvent;
import com.ksptool.ourcraft.sharedcore.world.SharedWorld;
import com.ksptool.ourcraft.sharedcore.world.WorldTemplateOld;
import com.ksptool.ourcraft.sharedcore.world.ChunkUtils;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端世界类，负责渲染数据的缓存和管理
 */
@Getter
public class ClientWorld implements SharedWorld {


    private final Map<Long, ClientChunk> chunks = new ConcurrentHashMap<>();

    //服务器发送到客户端的事件队列
    private final EventQueue eventQueue;
    
    //世界模板
    private final WorldTemplateOld template;
    
    private float timeOfDay = 0.0f;

    //客户端本地玩家
    private ClientPlayer player;
    private ChunkMeshGenerator chunkMeshGenerator;
    private final List<ClientChunk> dirtyChunks = new ArrayList<>();
    
    public ClientWorld(WorldTemplateOld template) {
        this.template = template;
        this.eventQueue = EventQueue.getInstance();
        this.chunkMeshGenerator = new ChunkMeshGenerator(this);
    }
    
    public void setPlayer(ClientPlayer player) {
        this.player = player;
    }
    
    /**
     * 处理服务器发送到客户端的事件队列中的所有事件
     */
    public void processEvents() {
        java.util.List<GameEvent> events = eventQueue.pollAllS2C();
        for (GameEvent event : events) {
            if (event instanceof BlockUpdateEvent) {
                handleBlockUpdate((BlockUpdateEvent) event);
            }
            if (event instanceof ChunkUpdateEvent) {
                handleChunkUpdate((ChunkUpdateEvent) event);
            }
            if (event instanceof TimeUpdateEvent) {
                handleTimeUpdate((TimeUpdateEvent) event);
            }
            if (event instanceof ChunkDataEvent) {
                handleChunkData((ChunkDataEvent) event);
            }
            if (event instanceof ChunkUnloadEvent) {
                handleChunkUnload((ChunkUnloadEvent) event);
            }
            if (event instanceof PlayerUpdateEvent) {
                handlePlayerUpdate((PlayerUpdateEvent) event);
            }
        }
    }
    
    private void handleBlockUpdate(BlockUpdateEvent event) {
        int chunkX = (int) Math.floor((float) event.getX() / ClientChunk.CHUNK_SIZE);
        int chunkZ = (int) Math.floor((float) event.getZ() / ClientChunk.CHUNK_SIZE);
        long key = ChunkUtils.getChunkKey(chunkX, chunkZ);
        
        ClientChunk clientChunk = chunks.get(key);
        if (clientChunk != null) {
            clientChunk.markNeedsMeshUpdate();
        }
    }
    
    private void handleChunkUpdate(ChunkUpdateEvent event) {
        long key = ChunkUtils.getChunkKey(event.getChunkX(), event.getChunkZ());
        ClientChunk clientChunk = chunks.get(key);
        if (clientChunk != null) {
            clientChunk.markNeedsMeshUpdate();
        }
    }
    
    private void handleTimeUpdate(TimeUpdateEvent event) {
        this.timeOfDay = event.getTimeOfDay();
    }
    
    private void handleChunkData(ChunkDataEvent event) {
        int chunkX = event.getChunkX();
        int chunkZ = event.getChunkZ();
        ClientChunk clientChunk = getChunk(chunkX, chunkZ);
        
        if (clientChunk == null) {
            clientChunk = new ClientChunk(chunkX, chunkZ);
            putChunk(chunkX, chunkZ, clientChunk);
        }
        
        int[][][] blockStates = event.getBlockStates();
        int[][][] copiedBlockStates = new int[ClientChunk.CHUNK_SIZE][ClientChunk.CHUNK_HEIGHT][ClientChunk.CHUNK_SIZE];
        for (int x = 0; x < ClientChunk.CHUNK_SIZE; x++) {
            for (int y = 0; y < ClientChunk.CHUNK_HEIGHT; y++) {
                for (int z = 0; z < ClientChunk.CHUNK_SIZE; z++) {
                    copiedBlockStates[x][y][z] = blockStates[x][y][z];
                }
            }
        }
        clientChunk.setBlockStates(copiedBlockStates);
        
        synchronized (dirtyChunks) {
            if (!dirtyChunks.contains(clientChunk)) {
                dirtyChunks.add(clientChunk);
            }
        }
    }
    
    private void handleChunkUnload(ChunkUnloadEvent event) {
        removeChunk(event.getChunkX(), event.getChunkZ());
    }
    
    private void handlePlayerUpdate(PlayerUpdateEvent event) {
        if (player == null) {
            return;
        }
        
        player.updateFromServer(event);
    }
    
    /**
     * 添加或更新客户端区块
     */
    public void putChunk(int chunkX, int chunkZ, ClientChunk clientChunk) {
        long key = ChunkUtils.getChunkKey(chunkX, chunkZ);
        chunks.put(key, clientChunk);
    }
    
    /**
     * 获取客户端区块
     */
    public ClientChunk getChunk(int chunkX, int chunkZ) {
        long key = ChunkUtils.getChunkKey(chunkX, chunkZ);
        return chunks.get(key);
    }
    
    /**
     * 移除客户端区块
     */
    public void removeChunk(int chunkX, int chunkZ) {
        long key = ChunkUtils.getChunkKey(chunkX, chunkZ);
        ClientChunk clientChunk = chunks.remove(key);
        if (clientChunk != null) {
            clientChunk.cleanup();
        }
    }
    
    /**
     * 获取所有需要更新网格的区块
     */
    public java.util.List<ClientChunk> getChunksNeedingMeshUpdate() {
        java.util.List<ClientChunk> result = new java.util.ArrayList<>();
        for (ClientChunk chunk : chunks.values()) {
            if (chunk.needsMeshUpdate()) {
                result.add(chunk);
            }
        }
        return result;
    }
    
    /**
     * 将区块标记为需要网格更新（用于网络接收的区块数据）
     */
    public void markChunkForMeshUpdate(int chunkX, int chunkZ) {
        ClientChunk chunk = getChunk(chunkX, chunkZ);
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
    
    /**
     * 获取指定坐标的方块状态（用于网格生成）
     */
    public int getBlockState(int x, int y, int z) {
        int chunkX = (int) Math.floor((float) x / ClientChunk.CHUNK_SIZE);
        int chunkZ = (int) Math.floor((float) z / ClientChunk.CHUNK_SIZE);
        ClientChunk clientChunk = getChunk(chunkX, chunkZ);
        if (clientChunk == null) {
            return 0;
        }
        int localX = x - chunkX * ClientChunk.CHUNK_SIZE;
        int localZ = z - chunkZ * ClientChunk.CHUNK_SIZE;
        return clientChunk.getBlockState(localX, y, localZ);
    }
    
    public void processMeshGeneration() {
        if (chunkMeshGenerator == null) {
            return;
        }
        
        List<java.util.concurrent.Future<MeshGenerationResult>> futures = chunkMeshGenerator.getPendingFutures();
        List<java.util.concurrent.Future<MeshGenerationResult>> completedFutures = new ArrayList<>();
        
        for (java.util.concurrent.Future<MeshGenerationResult> future : futures) {
            if (future.isDone()) {
                try {
                    MeshGenerationResult result = future.get();
                    if (result != null) {
                        ClientChunk clientChunk = getChunk(result.chunkX, result.chunkZ);
                        if (clientChunk != null) {
                            if (result.vertices.length > 0) {
                                Mesh mesh = new Mesh(
                                    result.vertices,
                                    result.texCoords,
                                    result.tints,
                                    result.animationData,
                                    result.indices
                                );
                                clientChunk.setMesh(mesh);
                            }
                            
                            if (result.transparentVertices.length > 0) {
                                Mesh transparentMesh = new Mesh(
                                    result.transparentVertices,
                                    result.transparentTexCoords,
                                    result.transparentTints,
                                    result.transparentAnimationData,
                                    result.transparentIndices
                                );
                                clientChunk.setTransparentMesh(transparentMesh);
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                completedFutures.add(future);
            }
        }
        
        futures.removeAll(completedFutures);
        
        synchronized (dirtyChunks) {
            for (ClientChunk clientChunk : dirtyChunks) {
                if (clientChunk.needsMeshUpdate()) {
                    chunkMeshGenerator.submitMeshTask(clientChunk);
                }
            }
            dirtyChunks.clear();
        }
    }
    
    public void cleanup() {
        if (chunkMeshGenerator != null) {
            chunkMeshGenerator.shutdown();
        }
        for (ClientChunk chunk : chunks.values()) {
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
    
}

