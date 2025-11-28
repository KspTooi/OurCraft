package com.ksptool.ourcraft.server.world;

import com.ksptool.ourcraft.server.OurCraftServer;
import com.ksptool.ourcraft.server.archive.ArchiveManager;
import com.ksptool.ourcraft.server.world.chunk.ServerChunk;
import com.ksptool.ourcraft.server.world.chunk.ServerSuperChunkManager;
import com.ksptool.ourcraft.server.world.gen.NoiseGenerator;
import com.ksptool.ourcraft.sharedcore.BoundingBox;
import com.ksptool.ourcraft.sharedcore.Registry;
import com.ksptool.ourcraft.sharedcore.events.EventQueue;
import com.ksptool.ourcraft.sharedcore.events.TimeUpdateEvent;
import com.ksptool.ourcraft.sharedcore.world.SharedWorld;
import com.ksptool.ourcraft.server.entity.ServerEntity;
import com.ksptool.ourcraft.server.entity.ServerPlayer;
import com.ksptool.ourcraft.sharedcore.world.WorldTemplate;
import com.ksptool.ourcraft.server.world.save.RegionManager;
import com.ksptool.ourcraft.sharedcore.world.gen.GenerationContext;
import com.ksptool.ourcraft.sharedcore.world.gen.TerrainGenerator;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3f;
import java.util.List;

/**
 * 服务端世界类，负责所有逻辑，不包含任何渲染相关代码
 */
@Getter
@Slf4j
public class ServerWorld implements SharedWorld {

    private static final int TICKS_PER_DAY = 24000;

    private final OurCraftServer server;
    
    private final WorldTemplate template;

    private double timeAccumulator = 0.0;

    private final ChunkManager chunkManager;

    private final ServerSuperChunkManager sscm;

    private final EntityManager entityManager;

    private final ServerCollisionManager collisionManager;
    
    @Setter
    private long gameTime = 0;
    
    @Setter
    private String name;

    @Setter
    private String seed;

    //@Getter
    //private RegionManager regionManager;

    @Getter
    private RegionManager entityRegionManager;

    @Getter
    private final GenerationContext generationContext;

    //地形生成器
    @Getter
    private final TerrainGenerator terrainGenerator;

    private final EventQueue eventQueue;

    @Setter
    private ArchiveManager archiveManager; //归档管理器
    
    public ServerWorld(OurCraftServer server,WorldTemplate template) {
        this.template = template;
        this.chunkManager = new ChunkManager(this);
        this.entityManager = new EntityManager(this);
        this.collisionManager = new ServerCollisionManager(this);
        this.seed = String.valueOf(System.currentTimeMillis());
        this.eventQueue = EventQueue.getInstance();
        this.sscm = new ServerSuperChunkManager(server,this);
        this.server = server;

        //从注册表获取地形生成器
        TerrainGenerator terrainGenerator = Registry.getInstance().getTerrainGenerator(template.getTerrainGenerator());

        if(terrainGenerator == null){
            throw new IllegalArgumentException("无法初始化世界: " + template.getStdRegName() + " 因为地形生成器未注册: " + template.getTerrainGenerator());
        }

        this.terrainGenerator = terrainGenerator;
        var noiseGenerator = new NoiseGenerator(seed);
        this.generationContext = new GenerationContext(noiseGenerator, this, seed);
    }
    
    public void setSaveName(String saveName) {
        //this.saveName = saveName;
        this.chunkManager.setSaveName(saveName);
        this.entityManager.setSaveName(saveName);
    }
    
    //public void setRegionManager(RegionManager regionManager) {
    //    this.regionManager = regionManager;
    //    this.chunkManager.setRegionManager(regionManager);
    //}
    
    public void setEntityRegionManager(RegionManager entityRegionManager) {
        this.entityRegionManager = entityRegionManager;
        this.entityManager.setEntityRegionManager(entityRegionManager);
    }

    public void init() {
        chunkManager.init();
    }

    /**
     * 新的update方法，由GameServer的主循环调用
     * 如果传入的deltaTime等于tickTime，则直接执行一次tick（固定时间步长模式）
     * 否则使用累加器模式处理可变时间增量（向后兼容）
     */
    public void update(float deltaTime, Vector3f playerPosition, Runnable playerTickCallback) {
        double tickTime = 1.0 / template.getTps();
        
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
        
        float tickDelta = 1.0f / template.getTps();
        
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
        if (template.getTps() == 0) {
            return 0.0f;
        }
        double tickTime = 1.0 / template.getTps();
        return (float) (timeAccumulator / tickTime);
    }

    public void generateChunkData(ServerChunk chunk) {
        if (terrainGenerator == null || generationContext == null) {
            return;
        }
        terrainGenerator.execute(chunk, generationContext);
    }
    
    public void generateChunkSynchronously(int chunkX, int chunkZ) {
        chunkManager.generateChunkSynchronously(chunkX, chunkZ);
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

    public void setBlockState(int x, int y, int z, int stateId) {
        chunkManager.setBlockState(x, y, z, stateId);
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

