package com.ksptool.ourcraft.server.world;

import com.ksptool.ourcraft.sharedcore.BoundingBox;
import com.ksptool.ourcraft.sharedcore.events.BlockUpdateEvent;
import com.ksptool.ourcraft.sharedcore.events.ChunkUpdateEvent;
import com.ksptool.ourcraft.sharedcore.events.EventQueue;
import com.ksptool.ourcraft.sharedcore.events.TimeUpdateEvent;
import com.ksptool.ourcraft.sharedcore.world.SharedWorld;
import com.ksptool.ourcraft.server.entity.ServerEntity;
import com.ksptool.ourcraft.server.entity.ServerPlayer;
import com.ksptool.ourcraft.sharedcore.world.GlobalPalette;
import com.ksptool.ourcraft.sharedcore.world.WorldTemplateOld;
import com.ksptool.ourcraft.sharedcore.world.BlockState;
import com.ksptool.ourcraft.server.world.gen.GenerationContext;
import com.ksptool.ourcraft.server.world.gen.TerrainPipeline;
import com.ksptool.ourcraft.server.world.save.RegionManager;
import com.ksptool.ourcraft.server.world.gen.layers.BaseDensityLayer;
import com.ksptool.ourcraft.server.world.gen.layers.FeatureLayer;
import com.ksptool.ourcraft.server.world.gen.layers.SurfaceLayer;
import com.ksptool.ourcraft.server.world.gen.layers.WaterLayer;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

import java.util.List;

/**
 * 服务端世界类，负责所有逻辑，不包含任何渲染相关代码
 */
@Getter
public class ServerWorld implements SharedWorld {
    private static final int TICKS_PER_DAY = 24000;
    
    private final WorldTemplateOld template;
    private double timeAccumulator = 0.0;
    
    private final ChunkManager chunkManager;
    private final EntityManager entityManager;
    private final ServerCollisionManager collisionManager;
    
    @Setter
    private long gameTime = 0;
    
    @Setter
    private String worldName;
    @Setter
    private long seed;
    @Getter
    private RegionManager regionManager;
    @Getter
    private RegionManager entityRegionManager;
    @Getter
    private String saveName;
    
    private NoiseGenerator noiseGenerator;
    private TerrainPipeline terrainPipeline;
    private GenerationContext generationContext;
    
    private final EventQueue eventQueue;
    
    public ServerWorld(WorldTemplateOld template) {
        this.template = template;
        this.chunkManager = new ChunkManager(this);
        this.entityManager = new EntityManager(this);
        this.collisionManager = new ServerCollisionManager(this);
        this.seed = System.currentTimeMillis();
        this.eventQueue = EventQueue.getInstance();
    }
    
    public void setSaveName(String saveName) {
        this.saveName = saveName;
        this.chunkManager.setSaveName(saveName);
        this.entityManager.setSaveName(saveName);
    }
    
    public void setRegionManager(RegionManager regionManager) {
        this.regionManager = regionManager;
        this.chunkManager.setRegionManager(regionManager);
    }
    
    public void setEntityRegionManager(RegionManager entityRegionManager) {
        this.entityRegionManager = entityRegionManager;
        this.entityManager.setEntityRegionManager(entityRegionManager);
    }

    public void init() {
        chunkManager.init();
        
        noiseGenerator = new NoiseGenerator(seed);
        terrainPipeline = new TerrainPipeline();
        terrainPipeline.addLayer(new BaseDensityLayer());
        terrainPipeline.addLayer(new WaterLayer());
        terrainPipeline.addLayer(new SurfaceLayer());
        terrainPipeline.addLayer(new FeatureLayer());
        generationContext = new GenerationContext(noiseGenerator, this, seed);
    }

    /**
     * 新的update方法，由GameServer的主循环调用
     * 如果传入的deltaTime等于tickTime，则直接执行一次tick（固定时间步长模式）
     * 否则使用累加器模式处理可变时间增量（向后兼容）
     */
    public void update(float deltaTime, Vector3f playerPosition, Runnable playerTickCallback) {
        double tickTime = 1.0 / template.getTicksPerSecond();
        
        // 如果传入的时间增量等于tickTime（固定时间步长），直接执行一次tick
        if (Math.abs(deltaTime - tickTime) < 0.001) {
            tick(playerPosition, playerTickCallback);
            return;
        }
        
        // 否则使用累加器模式（处理可变时间增量，向后兼容）
        timeAccumulator += deltaTime;
        while (timeAccumulator >= tickTime) {
            tick(playerPosition, playerTickCallback);
            timeAccumulator -= tickTime;
        }
    }
    
    /**
     * 单次逻辑更新（tick）
     */
    private void tick(Vector3f playerPosition, Runnable playerTickCallback) {
        for (ServerEntity entity : getEntities()) {
            entity.getPreviousPosition().set(entity.getPosition());
            if (entity instanceof ServerPlayer) {
                ServerPlayer player = (ServerPlayer) entity;
                player.setPreviousYaw(player.getYaw());
                player.setPreviousPitch(player.getPitch());
            }
        }
        
        gameTime++;
        chunkManager.update(playerPosition);
        
        eventQueue.offerS2C(new TimeUpdateEvent(getTimeOfDay()));
        
        float tickDelta = 1.0f / template.getTicksPerSecond();
        
        if (playerTickCallback != null) {
            playerTickCallback.run();
        }
        
        for (ServerEntity entity : getEntities()) {
            if (entity instanceof ServerPlayer) {
                continue;
            }
            entity.update(tickDelta);
        }
    }
    
    /**
     * 获取部分刻（Partial Tick），用于渲染插值
     * @return 0.0 到 1.0 之间的值，表示距离下一次tick的进度
     */
    public float getPartialTick() {
        if (template.getTicksPerSecond() == 0) {
            return 0.0f;
        }
        double tickTime = 1.0 / template.getTicksPerSecond();
        return (float) (timeAccumulator / tickTime);
    }

    public void generateChunkData(ServerChunk chunk) {
        if (terrainPipeline == null || generationContext == null) {
            return;
        }
        terrainPipeline.execute(chunk, generationContext);
    }
    
    public void generateChunkSynchronously(int chunkX, int chunkZ) {
        chunkManager.generateChunkSynchronously(chunkX, chunkZ);
    }
    
    public int getHeightAt(int worldX, int worldZ) {
        if (noiseGenerator == null) {
            return 64;
        }
        double noiseValue = noiseGenerator.noise(worldX * 0.05 + seed, worldZ * 0.05 + seed);
        return (int) (64 + noiseValue * 20);
    }
    
    public int getChunkCount() {
        return chunkManager.getChunkCount();
    }

    public int getBlockState(int x, int y, int z) {
        return chunkManager.getBlockState(x, y, z);
    }

    public ServerChunk getChunk(int chunkX, int chunkZ) {
        return chunkManager.getChunk(chunkX, chunkZ);
    }
    
    public ChunkManager getChunkManager() {
        return chunkManager;
    }
    
    public EntityManager getEntityManager() {
        return entityManager;
    }
    
    public void setBlockState(int x, int y, int z, int stateId) {
        int oldStateId = getBlockState(x, y, z);
        chunkManager.setBlockState(x, y, z, stateId);
        
        if (oldStateId != stateId) {
            GlobalPalette palette = GlobalPalette.getInstance();
            BlockState oldState = palette.getState(oldStateId);
            BlockState newState = palette.getState(stateId);
            
            if (oldState != null) {
                oldState.getSharedBlock().onBlockRemoved(this, x, y, z, oldState);
            }
            
            if (newState != null) {
                newState.getSharedBlock().onBlockAdded(this, x, y, z, newState);
            }
            
            eventQueue.offerS2C(new BlockUpdateEvent(x, y, z, stateId, oldStateId));
            
            int chunkX = (int) Math.floor((float) x / ServerChunk.CHUNK_SIZE);
            int chunkZ = (int) Math.floor((float) z / ServerChunk.CHUNK_SIZE);
            eventQueue.offerS2C(new ChunkUpdateEvent(chunkX, chunkZ));
            
            int[][] neighborOffsets = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
            for (int[] offset : neighborOffsets) {
                eventQueue.offerS2C(new ChunkUpdateEvent(chunkX + offset[0], chunkZ + offset[1]));
            }
        }
    }

    public boolean canMoveTo(Vector3f position, float height) {
        return collisionManager.canMoveTo(position, height);
    }

    public boolean canMoveTo(BoundingBox box) {
        return collisionManager.canMoveTo(box);
    }

    public void addEntity(ServerEntity entity) {
        entityManager.addEntity(entity);
    }

    public void removeEntity(ServerEntity entity) {
        entityManager.removeEntity(entity);
    }

    public List<ServerEntity> getEntities() {
        return entityManager.getEntities();
    }

    public void cleanup() {
        chunkManager.cleanup();
    }


    public void saveAllDirtyData() {
        chunkManager.saveAllDirtyChunks();
        entityManager.saveAllDirtyEntities();
    }
    
    public void saveToFile(String chunksDirPath) {
        saveAllDirtyData();
    }

    public void loadFromFile(String chunksDirPath) {
    }
    
    public float getTimeOfDay() {
        return (float) (gameTime % TICKS_PER_DAY) / TICKS_PER_DAY;
    }

    @Override
    public boolean isServerSide() {
        return true;
    }

    @Override
    public boolean isClientSide() {
        return false;
    }
}

