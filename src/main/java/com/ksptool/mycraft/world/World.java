package com.ksptool.mycraft.world;

import com.ksptool.mycraft.entity.BoundingBox;
import com.ksptool.mycraft.entity.Camera;
import com.ksptool.mycraft.entity.Entity;
import com.ksptool.mycraft.rendering.Frustum;
import com.ksptool.mycraft.rendering.ShaderProgram;
import com.ksptool.mycraft.rendering.TextureManager;
import com.ksptool.mycraft.world.save.ChunkSerializer;
import com.ksptool.mycraft.world.save.EntitySerializer;
import com.ksptool.mycraft.world.save.RegionFile;
import com.ksptool.mycraft.world.save.RegionManager;
import com.ksptool.mycraft.world.gen.GenerationContext;
import com.ksptool.mycraft.world.gen.TerrainPipeline;
import com.ksptool.mycraft.world.gen.layers.BaseDensityLayer;
import com.ksptool.mycraft.world.gen.layers.FeatureLayer;
import com.ksptool.mycraft.world.gen.layers.SurfaceLayer;
import com.ksptool.mycraft.world.gen.layers.WaterLayer;
import org.apache.commons.lang3.StringUtils;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 世界管理类，负责区块管理、实体管理、世界生成、时间系统和渲染
 */
public class World {
    private static final Logger logger = LoggerFactory.getLogger(World.class);
    private Map<Long, Chunk> chunks;
    private static final int RENDER_DISTANCE = 8;
    private int textureId;
    private long gameTime = 0;
    private static final int TICKS_PER_DAY = 24000;
    private static final float TIME_SPEED = 1.0f;
    
    private final BlockingQueue<ChunkGenerationTask> generationQueue;
    private final Map<Long, ChunkGenerationTask> pendingChunks;
    private WorldGenerator worldGenerator;
    private ChunkMeshGenerator chunkMeshGenerator;
    private Frustum frustum;
    
    private final List<Entity> entities;
    
    private int lastPlayerChunkX = Integer.MIN_VALUE;
    private int lastPlayerChunkZ = Integer.MIN_VALUE;
    
    private String worldName;
    private long seed;
    private RegionManager regionManager;
    private RegionManager entityRegionManager;
    private String saveName;
    
    private NoiseGenerator noiseGenerator;
    private TerrainPipeline terrainPipeline;
    private GenerationContext generationContext;

    private static long getChunkKey(int x, int z) {
        return ((long)x << 32) | (z & 0xFFFFFFFFL);
    }

    public World() {
        this.chunks = new ConcurrentHashMap<>();
        this.generationQueue = new LinkedBlockingQueue<>();
        this.pendingChunks = new ConcurrentHashMap<>();
        this.entities = new ArrayList<>();
        this.frustum = new Frustum();
        this.seed = System.currentTimeMillis();
    }
    
    public void setSaveName(String saveName) {
        this.saveName = saveName;
    }
    
    public void setRegionManager(RegionManager regionManager) {
        this.regionManager = regionManager;
    }
    
    public RegionManager getRegionManager() {
        return regionManager;
    }
    
    public void setEntityRegionManager(RegionManager entityRegionManager) {
        this.entityRegionManager = entityRegionManager;
    }
    
    public RegionManager getEntityRegionManager() {
        return entityRegionManager;
    }

    public void init() {
        loadTexture();
        chunkMeshGenerator = new ChunkMeshGenerator(this);
        
        noiseGenerator = new NoiseGenerator(seed);
        terrainPipeline = new TerrainPipeline();
        terrainPipeline.addLayer(new BaseDensityLayer());
        terrainPipeline.addLayer(new WaterLayer());
        terrainPipeline.addLayer(new SurfaceLayer());
        terrainPipeline.addLayer(new FeatureLayer());
        generationContext = new GenerationContext(noiseGenerator, this, seed);
        
        worldGenerator = new WorldGenerator(this, generationQueue);
        worldGenerator.start();
    }

    private void loadTexture() {
        TextureManager textureManager = TextureManager.getInstance();
        textureManager.loadAtlas();
        
        textureId = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);

        int[] pixels = textureManager.getAtlasPixels();
        int atlasWidth = textureManager.getAtlasWidth();
        int atlasHeight = textureManager.getAtlasHeight();

        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocateDirect(atlasWidth * atlasHeight * 4);
        for (int pixel : pixels) {
            buffer.put((byte) ((pixel >> 16) & 0xFF));
            buffer.put((byte) ((pixel >> 8) & 0xFF));
            buffer.put((byte) (pixel & 0xFF));
            buffer.put((byte) ((pixel >> 24) & 0xFF));
        }
        buffer.flip();

        org.lwjgl.opengl.GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, atlasWidth, atlasHeight, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL12.GL_TEXTURE_WRAP_S, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL12.GL_TEXTURE_WRAP_T, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
    }

    public void update(Vector3f playerPosition) {
        gameTime += TIME_SPEED;

        int playerChunkX = (int) Math.floor(playerPosition.x / Chunk.CHUNK_SIZE);
        int playerChunkZ = (int) Math.floor(playerPosition.z / Chunk.CHUNK_SIZE);

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
                        Chunk chunk = task.getChunk();
                        if (!chunks.containsKey(key)) {
                            chunks.put(key, chunk);
                        }
                        if (chunk.getState() == Chunk.ChunkState.DATA_LOADED) {
                            chunkMeshGenerator.submitMeshTask(chunk);
                            chunk.setState(Chunk.ChunkState.AWAITING_MESH);
                        }
                    }
                }
            }
            
            chunks.entrySet().removeIf(entry -> {
                long key = entry.getKey();
                Chunk chunk = entry.getValue();
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
                            logger.debug("卸载时保存脏区块 [{},{}]", chunkX, chunkZ);
                            byte[] compressedData = ChunkSerializer.serialize(chunk);
                            RegionFile regionFile = regionManager.getRegionFile(regionX, regionZ);
                            regionFile.open();
                            regionFile.writeChunk(localX, localZ, compressedData);
                            chunk.markDirty(false);
                        } catch (Exception e) {
                            logger.error("卸载时保存区块失败 [{},{}]", chunkX, chunkZ, e);
                        }
                    }
                    if (chunk.areEntitiesDirty() && entityRegionManager != null && StringUtils.isNotBlank(saveName)) {
                        logger.debug("卸载时保存脏实体区块 [{},{}]", chunkX, chunkZ);
                        saveEntitiesForChunk(chunkX, chunkZ);
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

    public void generateChunkData(Chunk chunk) {
        if (terrainPipeline == null || generationContext == null) {
            return;
        }
        terrainPipeline.execute(chunk, generationContext);
    }
    
    public void generateChunkSynchronously(int chunkX, int chunkZ) {
        // 首先，尝试从内存或磁盘加载区块。getChunk 已经包含了这个逻辑。
        Chunk chunk = getChunk(chunkX, chunkZ);

        // 如果区块为 null，意味着它在任何地方都不存在，此时才生成新的。
        if (chunk == null) {
            long key = getChunkKey(chunkX, chunkZ);
            chunk = new Chunk(chunkX, chunkZ);
            generateChunkData(chunk);
            chunks.put(key, chunk);
        }

        // 确保区块（无论是新生成的还是从磁盘加载的）准备好进行渲染。
        if (chunk.getState() == Chunk.ChunkState.DATA_LOADED || chunk.getState() == Chunk.ChunkState.NEW) {
            MeshGenerationResult result = chunk.calculateMeshData(this);
            if (result != null) {
                chunk.uploadToGPU(result);
            }
        }
    }
    
    public int getHeightAt(int worldX, int worldZ) {
        if (noiseGenerator == null) {
            return 64;
        }
        double noiseValue = noiseGenerator.noise(worldX * 0.05 + seed, worldZ * 0.05 + seed);
        return (int) (64 + noiseValue * 20);
    }

    public void render(ShaderProgram shader, Camera camera) {
        renderOpaque(shader, camera);
        renderTransparent(shader, camera);
    }

    public void renderOpaque(ShaderProgram shader, Camera camera) {
        if (chunks.isEmpty()) {
            return;
        }
        
        frustum.update(camera.getProjectionMatrix(), camera.getViewMatrix());
        
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        shader.setUniform("textureSampler", 0);

        Vector3f playerPosition = camera.getPosition();
        int playerChunkX = (int) Math.floor(playerPosition.x / Chunk.CHUNK_SIZE);
        int playerChunkZ = (int) Math.floor(playerPosition.z / Chunk.CHUNK_SIZE);

        for (int x = playerChunkX - RENDER_DISTANCE; x <= playerChunkX + RENDER_DISTANCE; x++) {
            for (int z = playerChunkZ - RENDER_DISTANCE; z <= playerChunkZ + RENDER_DISTANCE; z++) {
                
                long key = getChunkKey(x, z);
                Chunk chunk = chunks.get(key);

                if (chunk != null && chunk.hasMesh()) {
                    if (frustum.intersects(chunk.getBoundingBox())) {
                        chunk.render();
                    }
                }
            }
        }
    }

    public void renderTransparent(ShaderProgram shader, Camera camera) {
        if (chunks.isEmpty()) {
            return;
        }
        
        frustum.update(camera.getProjectionMatrix(), camera.getViewMatrix());
        
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        shader.setUniform("textureSampler", 0);

        Vector3f playerPosition = camera.getPosition();
        int playerChunkX = (int) Math.floor(playerPosition.x / Chunk.CHUNK_SIZE);
        int playerChunkZ = (int) Math.floor(playerPosition.z / Chunk.CHUNK_SIZE);

        for (int x = playerChunkX - RENDER_DISTANCE; x <= playerChunkX + RENDER_DISTANCE; x++) {
            for (int z = playerChunkZ - RENDER_DISTANCE; z <= playerChunkZ + RENDER_DISTANCE; z++) {
                
                long key = getChunkKey(x, z);
                Chunk chunk = chunks.get(key);

                if (chunk != null && chunk.hasTransparentMesh()) {
                    if (frustum.intersects(chunk.getBoundingBox())) {
                        chunk.renderTransparent();
                    }
                }
            }
        }
    }
    
    public int getChunkCount() {
        return chunks.size();
    }

    public int getBlockState(int x, int y, int z) {
        int chunkX = (int) Math.floor((float) x / Chunk.CHUNK_SIZE);
        int chunkZ = (int) Math.floor((float) z / Chunk.CHUNK_SIZE);
        long key = getChunkKey(chunkX, chunkZ);
        Chunk chunk = chunks.get(key);
        if (chunk == null) {
            return 0;
        }
        int localX = x - chunkX * Chunk.CHUNK_SIZE;
        int localZ = z - chunkZ * Chunk.CHUNK_SIZE;
        return chunk.getBlockState(localX, y, localZ);
    }

    public Chunk getChunk(int chunkX, int chunkZ) {
        long key = getChunkKey(chunkX, chunkZ);
        Chunk chunk = chunks.get(key);
        
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
    
    private Chunk loadChunkFromRegion(int chunkX, int chunkZ) {
        if (regionManager == null) {
            logger.debug("无法加载区块 [{},{}]: 区域管理器未初始化", chunkX, chunkZ);
            return null;
        }
        
        try {
            int regionX = RegionManager.getRegionX(chunkX);
            int regionZ = RegionManager.getRegionZ(chunkZ);
            int localX = RegionManager.getLocalChunkX(chunkX);
            int localZ = RegionManager.getLocalChunkZ(chunkZ);
            
            logger.debug("动态加载区块 [{},{}] 从区域 [{},{}] 本地坐标 [{},{}]", chunkX, chunkZ, regionX, regionZ, localX, localZ);
            
            RegionFile regionFile = regionManager.getRegionFile(regionX, regionZ);
            regionFile.open();
            
            byte[] compressedData = regionFile.readChunk(localX, localZ);
            if (compressedData == null) {
                logger.debug("区块 [{},{}] 不存在于区域文件中", chunkX, chunkZ);
                return null;
            }
            
            Chunk chunk = ChunkSerializer.deserialize(compressedData, chunkX, chunkZ);
            
            if (chunk != null) {
                logger.debug("成功加载区块 [{},{}]", chunkX, chunkZ);
                if (entityRegionManager != null) {
                    loadEntitiesForChunk(chunkX, chunkZ);
                }
            } else {
                logger.warn("区块 [{},{}] 反序列化失败", chunkX, chunkZ);
            }
            
            return chunk;
        } catch (Exception e) {
            logger.error("加载区块失败 [{},{}]", chunkX, chunkZ, e);
            return null;
        }
    }
    
    private void loadEntitiesForChunk(int chunkX, int chunkZ) {
        if (entityRegionManager == null) {
            return;
        }
        
        try {
            int regionX = RegionManager.getRegionX(chunkX);
            int regionZ = RegionManager.getRegionZ(chunkZ);
            int localX = RegionManager.getLocalChunkX(chunkX);
            int localZ = RegionManager.getLocalChunkZ(chunkZ);
            
            RegionFile entityRegionFile = entityRegionManager.getRegionFile(regionX, regionZ);
            entityRegionFile.open();
            
            byte[] compressedData = entityRegionFile.readChunk(localX, localZ);
            if (compressedData == null) {
                return;
            }
            
            List<Entity> loadedEntities = EntitySerializer.deserialize(compressedData, this);
            if (loadedEntities != null && !loadedEntities.isEmpty()) {
                logger.debug("从区块 [{},{}] 加载了 {} 个实体", chunkX, chunkZ, loadedEntities.size());
                for (Entity entity : loadedEntities) {
                    if (!entities.contains(entity)) {
                        entities.add(entity);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("加载实体失败 [{},{}]", chunkX, chunkZ, e);
        }
    }
    
    private void saveEntitiesForChunk(int chunkX, int chunkZ) {
        if (entityRegionManager == null || StringUtils.isBlank(saveName)) {
            return;
        }
        
        try {
            List<Entity> chunkEntities = new ArrayList<>();
            float chunkMinX = chunkX * Chunk.CHUNK_SIZE;
            float chunkMaxX = chunkMinX + Chunk.CHUNK_SIZE;
            float chunkMinZ = chunkZ * Chunk.CHUNK_SIZE;
            float chunkMaxZ = chunkMinZ + Chunk.CHUNK_SIZE;
            
            for (Entity entity : entities) {
                Vector3f pos = entity.getPosition();
                if (pos.x >= chunkMinX && pos.x < chunkMaxX && 
                    pos.z >= chunkMinZ && pos.z < chunkMaxZ) {
                    chunkEntities.add(entity);
                }
            }
            
            if (chunkEntities.isEmpty()) {
                return;
            }
            
            byte[] compressedData = EntitySerializer.serialize(chunkEntities);
            
            int regionX = RegionManager.getRegionX(chunkX);
            int regionZ = RegionManager.getRegionZ(chunkZ);
            int localX = RegionManager.getLocalChunkX(chunkX);
            int localZ = RegionManager.getLocalChunkZ(chunkZ);
            
            RegionFile entityRegionFile = entityRegionManager.getRegionFile(regionX, regionZ);
            entityRegionFile.open();
            entityRegionFile.writeChunk(localX, localZ, compressedData);
            logger.debug("成功保存区块 [{},{}] 的 {} 个实体", chunkX, chunkZ, chunkEntities.size());
        } catch (Exception e) {
            logger.error("保存实体失败 [{},{}]", chunkX, chunkZ, e);
        }
    }
    
    public void setBlockState(int x, int y, int z, int stateId) {
        int chunkX = (int) Math.floor((float) x / Chunk.CHUNK_SIZE);
        int chunkZ = (int) Math.floor((float) z / Chunk.CHUNK_SIZE);
        long key = getChunkKey(chunkX, chunkZ);
        Chunk chunk = chunks.get(key);
        if (chunk == null) {
            chunk = getChunk(chunkX, chunkZ);
            if (chunk == null) {
                return;
            }
        }
        int localX = x - chunkX * Chunk.CHUNK_SIZE;
        int localZ = z - chunkZ * Chunk.CHUNK_SIZE;
        chunk.setBlockState(localX, y, localZ, stateId);
        if (chunk.getState() == Chunk.ChunkState.READY) {
            chunk.setState(Chunk.ChunkState.DATA_LOADED);
        }
        chunkMeshGenerator.submitMeshTask(chunk);
        chunk.setState(Chunk.ChunkState.AWAITING_MESH);

        int[][] neighborOffsets = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] offset : neighborOffsets) {
            long neighborKey = getChunkKey(chunkX + offset[0], chunkZ + offset[1]);
            Chunk neighborChunk = chunks.get(neighborKey);
            if (neighborChunk != null) {
                if (neighborChunk.getState() == Chunk.ChunkState.READY) {
                    neighborChunk.setState(Chunk.ChunkState.DATA_LOADED);
                }
                chunkMeshGenerator.submitMeshTask(neighborChunk);
                neighborChunk.setState(Chunk.ChunkState.AWAITING_MESH);
            }
        }
    }

    public boolean canMoveTo(Vector3f position, float height) {
        int minX = (int) Math.floor(position.x - 0.3f);
        int maxX = (int) Math.floor(position.x + 0.3f);
        int minY = (int) Math.floor(position.y);
        int maxY = (int) Math.floor(position.y + height);
        int minZ = (int) Math.floor(position.z - 0.3f);
        int maxZ = (int) Math.floor(position.z + 0.3f);

        GlobalPalette palette = GlobalPalette.getInstance();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    int stateId = getBlockState(x, y, z);
                    BlockState state = palette.getState(stateId);
                    Block block = state.getBlock();
                    if (block.isSolid()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public boolean canMoveTo(BoundingBox box) {
        int minX = (int) Math.floor(box.getMinX());
        int maxX = (int) Math.floor(box.getMaxX());
        int minY = (int) Math.floor(box.getMinY());
        int maxY = (int) Math.floor(box.getMaxY());
        int minZ = (int) Math.floor(box.getMinZ());
        int maxZ = (int) Math.floor(box.getMaxZ());

        GlobalPalette palette = GlobalPalette.getInstance();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    int stateId = getBlockState(x, y, z);
                    BlockState state = palette.getState(stateId);
                    Block block = state.getBlock();
                    if (block.isSolid()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public void addEntity(Entity entity) {
        entities.add(entity);
        entity.markDirty(true);
    }

    public void removeEntity(Entity entity) {
        entities.remove(entity);
    }

    public List<Entity> getEntities() {
        return entities;
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
        
        for (Chunk chunk : chunks.values()) {
            chunk.cleanup();
        }
        chunks.clear();
        pendingChunks.clear();
        generationQueue.clear();
        if (chunkMeshGenerator != null) {
            chunkMeshGenerator.shutdown();
        }
        GL11.glDeleteTextures(textureId);
    }

    public float getTimeOfDay() {
        return (float) (gameTime % TICKS_PER_DAY) / TICKS_PER_DAY;
    }

    public long getGameTime() {
        return gameTime;
    }

    public void setGameTime(long gameTime) {
        this.gameTime = gameTime;
    }

    public org.joml.Vector3f getSkyColor() {
        float t = getTimeOfDay();
        
        Vector3f midnight = new Vector3f(0.05f, 0.05f, 0.15f);
        Vector3f sunriseStart = new Vector3f(1.0f, 0.5f, 0.1f);
        Vector3f sunriseEnd = new Vector3f(0.4f, 0.7f, 0.9f);
        Vector3f noon = new Vector3f(0.48f, 0.75f, 0.94f);
        Vector3f sunsetStart = new Vector3f(0.4f, 0.7f, 0.9f);
        Vector3f sunsetEnd = new Vector3f(1.0f, 0.5f, 0.1f);
        
        if (t < 0.125f) {
            float factor = t / 0.125f;
            factor = (float) ((1.0f - Math.cos(factor * Math.PI)) * 0.5f);
            return new Vector3f(
                midnight.x + (sunriseStart.x - midnight.x) * factor,
                midnight.y + (sunriseStart.y - midnight.y) * factor,
                midnight.z + (sunriseStart.z - midnight.z) * factor
            );
        }
        if (t < 0.25f) {
            float factor = (t - 0.125f) / 0.125f;
            factor = (float) ((1.0f - Math.cos(factor * Math.PI)) * 0.5f);
            return new Vector3f(
                sunriseStart.x + (sunriseEnd.x - sunriseStart.x) * factor,
                sunriseStart.y + (sunriseEnd.y - sunriseStart.y) * factor,
                sunriseStart.z + (sunriseEnd.z - sunriseStart.z) * factor
            );
        }
        if (t < 0.5f) {
            float factor = (t - 0.25f) / 0.25f;
            factor = (float) ((1.0f - Math.cos(factor * Math.PI)) * 0.5f);
            return new Vector3f(
                sunriseEnd.x + (noon.x - sunriseEnd.x) * factor,
                sunriseEnd.y + (noon.y - sunriseEnd.y) * factor,
                sunriseEnd.z + (noon.z - sunriseEnd.z) * factor
            );
        }
        if (t < 0.75f) {
            float factor = (t - 0.5f) / 0.25f;
            factor = (float) ((1.0f - Math.cos(factor * Math.PI)) * 0.5f);
            return new Vector3f(
                noon.x + (sunsetStart.x - noon.x) * factor,
                noon.y + (sunsetStart.y - noon.y) * factor,
                noon.z + (sunsetStart.z - noon.z) * factor
            );
        }
        if (t < 0.875f) {
            float factor = (t - 0.75f) / 0.125f;
            factor = (float) ((1.0f - Math.cos(factor * Math.PI)) * 0.5f);
            return new Vector3f(
                sunsetStart.x + (sunsetEnd.x - sunsetStart.x) * factor,
                sunsetStart.y + (sunsetEnd.y - sunsetStart.y) * factor,
                sunsetStart.z + (sunsetEnd.z - sunsetStart.z) * factor
            );
        }
        float factor = (t - 0.875f) / 0.125f;
        factor = (float) ((1.0f - Math.cos(factor * Math.PI)) * 0.5f);
        return new Vector3f(
            sunsetEnd.x + (midnight.x - sunsetEnd.x) * factor,
            sunsetEnd.y + (midnight.y - sunsetEnd.y) * factor,
            sunsetEnd.z + (midnight.z - sunsetEnd.z) * factor
        );
    }

    public org.joml.Vector3f getAmbientLightColor() {
        float t = getTimeOfDay();
        Vector3f midnight = new Vector3f(0.1f, 0.1f, 0.15f);
        Vector3f noon = new Vector3f(1.0f, 1.0f, 1.0f);
        
        float factor;
        if (t < 0.5f) {
            factor = t / 0.5f;
        } else {
            factor = (1.0f - t) / 0.5f;
        }
        factor = (float) ((1.0f - Math.cos(factor * Math.PI)) * 0.5f);
        
        return new Vector3f(
            midnight.x + (noon.x - midnight.x) * factor,
            midnight.y + (noon.y - midnight.y) * factor,
            midnight.z + (noon.z - midnight.z) * factor
        );
    }

    public ChunkMeshGenerator getChunkMeshGenerator() {
        return chunkMeshGenerator;
    }

    public String getWorldName() {
        return worldName;
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    public long getSeed() {
        return seed;
    }

    public void setSeed(long seed) {
        this.seed = seed;
    }

    public void saveAllDirtyData() {
        if (regionManager == null || StringUtils.isBlank(saveName)) {
            logger.debug("跳过保存: 区域管理器未初始化或存档名称为空");
            return;
        }
        
        try {
            int dirtyChunkCount = 0;
            int dirtyEntityChunkCount = 0;
            
            for (Map.Entry<Long, Chunk> entry : chunks.entrySet()) {
                Chunk chunk = entry.getValue();
                if (chunk == null) {
                    continue;
                }
                
                long key = entry.getKey();
                int chunkX = chunk.getChunkX();
                int chunkZ = chunk.getChunkZ();
                
                if (chunk.isDirty()) {
                    int regionX = RegionManager.getRegionX(chunkX);
                    int regionZ = RegionManager.getRegionZ(chunkZ);
                    int localX = RegionManager.getLocalChunkX(chunkX);
                    int localZ = RegionManager.getLocalChunkZ(chunkZ);
                    
                    logger.debug("保存脏区块 [{},{}] 到区域 [{},{}] 本地坐标 [{},{}]", chunkX, chunkZ, regionX, regionZ, localX, localZ);
                    
                    byte[] compressedData = ChunkSerializer.serialize(chunk);
                    
                    RegionFile regionFile = regionManager.getRegionFile(regionX, regionZ);
                    regionFile.open();
                    regionFile.writeChunk(localX, localZ, compressedData);
                    
                    chunk.markDirty(false);
                    dirtyChunkCount++;
                }
                
                if (chunk.areEntitiesDirty()) {
                    logger.debug("保存脏实体区块 [{},{}]", chunkX, chunkZ);
                    saveEntitiesForChunk(chunkX, chunkZ);
                    chunk.markEntitiesDirty(false);
                    dirtyEntityChunkCount++;
                    
                    for (Entity entity : entities) {
                        Vector3f pos = entity.getPosition();
                        int entityChunkX = (int) Math.floor(pos.x / Chunk.CHUNK_SIZE);
                        int entityChunkZ = (int) Math.floor(pos.z / Chunk.CHUNK_SIZE);
                        if (entityChunkX == chunkX && entityChunkZ == chunkZ) {
                            entity.markDirty(false);
                        }
                    }
                }
            }
            
            if (dirtyChunkCount > 0 || dirtyEntityChunkCount > 0) {
                logger.info("保存完成: 脏区块数={}, 脏实体区块数={}", dirtyChunkCount, dirtyEntityChunkCount);
            } else {
                logger.debug("没有需要保存的脏数据");
            }
        } catch (Exception e) {
            logger.error("保存区块失败", e);
        }
    }
    
    public void saveToFile(String chunksDirPath) {
        saveAllDirtyData();
    }

    public void loadFromFile(String chunksDirPath) {
    }
}

