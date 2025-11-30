package com.ksptool.ourcraft.server.world;

import com.ksptool.ourcraft.server.OurCraftServer;
import com.ksptool.ourcraft.server.archive.ArchiveService;
import com.ksptool.ourcraft.server.archive.model.ArchiveWorldIndexDto;
import com.ksptool.ourcraft.server.world.chunk.ChunkManagerOld;
import com.ksptool.ourcraft.server.world.chunk.ServerChunkOld;
import com.ksptool.ourcraft.server.world.chunk.FlexServerChunkService;
import com.ksptool.ourcraft.server.world.gen.NoiseGenerator;
import com.ksptool.ourcraft.sharedcore.BoundingBox;
import com.ksptool.ourcraft.sharedcore.Registry;
import com.ksptool.ourcraft.sharedcore.events.EventQueue;
import com.ksptool.ourcraft.sharedcore.events.TimeUpdateEvent;
import com.ksptool.ourcraft.sharedcore.enums.BlockEnums;
import com.ksptool.ourcraft.sharedcore.utils.position.Pos;
import com.ksptool.ourcraft.sharedcore.world.BlockState;
import com.ksptool.ourcraft.sharedcore.world.SharedWorld;
import com.ksptool.ourcraft.sharedcore.world.WorldEvent;
import com.ksptool.ourcraft.server.entity.ServerEntity;
import com.ksptool.ourcraft.server.entity.ServerPlayer;
import com.ksptool.ourcraft.server.event.ServerPlayerCameraInputEvent;
import com.ksptool.ourcraft.server.event.ServerPlayerInputEvent;
import com.ksptool.ourcraft.sharedcore.world.WorldTemplate;
import com.ksptool.ourcraft.server.world.save.RegionManager;
import com.ksptool.ourcraft.sharedcore.world.gen.GenerationContext;
import com.ksptool.ourcraft.sharedcore.world.gen.TerrainGenerator;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务端世界类，负责所有逻辑，不包含任何渲染相关代码
 */
@Getter
@Slf4j
public class ServerWorld implements SharedWorld {

    private static final int TICKS_PER_DAY = 24000;

    //该世界的默认玩家出生点
    @Setter
    private Pos defaultSpawnPos;

    private final OurCraftServer server;
    
    private final WorldTemplate template;

    private double timeAccumulator = 0.0;

    private final ChunkManagerOld chunkManagerOld;

    private final FlexServerChunkService flexServerChunkService;

    private final EntityServiceOld entityService;

    private final ServerCollisionManager collisionManager;
    
    @Setter
    private long totalTicks = 0;
    
    @Setter
    private String name;

    @Setter
    private String seed;

    @Getter
    private RegionManager entityRegionManager;

    @Getter
    private final GenerationContext generationContext;

    //地形生成器
    @Getter
    private final TerrainGenerator terrainGenerator;

    private final EventQueue eventQueue;

    //服务端世界事件总线
    private final ServerWorldEventBus eventBus;

    @Setter
    private ArchiveService archiveService;

    public ServerWorld(OurCraftServer server,WorldTemplate template) {
        this.template = template;
        this.chunkManagerOld = new ChunkManagerOld(this);
        this.entityService = new EntityServiceOld(this);
        this.collisionManager = new ServerCollisionManager(this);
        this.seed = String.valueOf(System.currentTimeMillis());
        this.eventQueue = EventQueue.getInstance();
        this.flexServerChunkService = new FlexServerChunkService(server,this);
        this.server = server;

        //从注册表获取地形生成器
        TerrainGenerator terrainGenerator = Registry.getInstance().getTerrainGenerator(template.getTerrainGenerator());

        if(terrainGenerator == null){
            throw new IllegalArgumentException("无法初始化世界: " + template.getStdRegName() + " 因为地形生成器未注册: " + template.getTerrainGenerator());
        }

        this.terrainGenerator = terrainGenerator;
        var noiseGenerator = new NoiseGenerator(seed);
        this.generationContext = new GenerationContext(noiseGenerator, this, seed);
        this.eventBus = new ServerWorldEventBus();
    }
    
    public void setSaveName(String saveName) {
        //this.saveName = saveName;
        this.chunkManagerOld.setSaveName(saveName);
        this.entityService.setSaveName(saveName);
    }

    public void setEntityRegionManager(RegionManager entityRegionManager) {
        this.entityRegionManager = entityRegionManager;
        this.entityService.setEntityRegionManager(entityRegionManager);
    }

    public void init() {
        chunkManagerOld.init();
        //创建默认出生点
        createDefaultSpawn();
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

        totalTicks++;
        chunkManagerOld.update(playerPosition);
        
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

    /**
     * 执行世界逻辑
     * @param delta 距离上一帧经过的时间（秒）由SWEU传入
     */
    @Override
    public void action(double delta) {

        //世界时间推进
        totalTicks += delta;

        Map<Long, List<WorldEvent>> actionPlayerEvents = new HashMap<>();
        List<WorldEvent> actionOtherEvents = new ArrayList<>();
        
        //循环拉取事件 并将玩家事件按SessionID分组
        while (eventBus.hasNext()) {
            var event = eventBus.next();
            if (event instanceof ServerPlayerCameraInputEvent e) {
                actionPlayerEvents.computeIfAbsent(e.getSessionId(), k -> new ArrayList<>()).add(event);
                continue;
            }
            if (event instanceof ServerPlayerInputEvent e) {
                actionPlayerEvents.computeIfAbsent(e.getSessionId(), k -> new ArrayList<>()).add(event);
                continue;
            }
            //其他事件 不分组
            actionOtherEvents.add(event);
        }

        //优先处理已分组的玩家事件
        for (Map.Entry<Long, List<WorldEvent>> entry : actionPlayerEvents.entrySet()) {

            var player = entityService.getPlayerBySessionId(entry.getKey());

            if (player == null) {
                log.warn("世界:{} 无法找到玩家会话ID:{} 对应的玩家实体", name, entry.getKey());
                continue;
            }

            //应用玩家输入
            for (WorldEvent event : entry.getValue()) {
                //应用玩家相机视角输入事件
                if (event instanceof ServerPlayerCameraInputEvent e) {
                    player.updateCameraOrientation(e.getDeltaYaw(), e.getDeltaPitch());
                }

                //应用玩家键盘输入事件
                if (event instanceof ServerPlayerInputEvent e) {
                    player.applyInput(e);
                }
            }
        }

        //TODO: 处理其他事件


        //实体物理模拟
        for (ServerEntity entity : getEntities()) {
            entity.update(delta);
        }



        //计算玩家移动逻辑以及票据
        var allEntities = entityService.getEntities();

        //遍历在现在还在这个世界的所有玩家
        for (ServerEntity entity : allEntities) {
            if (!(entity instanceof ServerPlayer)) {
                continue;
            }

        }



    }

    /**
     * 创建玩家在这个世界的默认出生点
     */
    public void createDefaultSpawn(){

        //查询归档中的世界索引
        var worldIndex = server.getArchiveService().getWorldService().loadWorldIndex(name);

        if (worldIndex == null) {
            throw new IllegalArgumentException("无法创建玩家在这个世界的默认出生点: " + name + " 因为世界索引未找到");
        }

        //0:未创建, 1:已创建
        if (worldIndex.getDefaultSpawnCreated() == 1) {
            var defaultSpawnPos = Pos.of(worldIndex.getDefaultSpawnX(), worldIndex.getDefaultSpawnY(), worldIndex.getDefaultSpawnZ());
            this.defaultSpawnPos = defaultSpawnPos;
            log.info("世界:{} 的默认出生点为:{}", name, defaultSpawnPos);
            return;
        }

        //创建默认出生点
        int searchRadius = 2;
        int bestSpawnY = -1;
        int bestSpawnX = 0;
        int bestSpawnZ = 0;

        for (int chunkX = -searchRadius; chunkX <= searchRadius; chunkX++) {
            for (int chunkZ = -searchRadius; chunkZ <= searchRadius; chunkZ++) {
                generateChunkSynchronously(chunkX, chunkZ);
                ServerChunkOld chunk = getChunk(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }

                for (int localX = 0; localX < ServerChunkOld.CHUNK_SIZE; localX++) {
                    for (int localZ = 0; localZ < ServerChunkOld.CHUNK_SIZE; localZ++) {
                        for (int y = ServerChunkOld.CHUNK_HEIGHT - 1; y >= 0; y--) {
                            BlockState state = chunk.getBlockState(localX, y, localZ);
                            if (state == null) {
                                continue;
                            }
                            if (state.getSharedBlock() == null) {
                                continue;
                            }
                            if (!state.getSharedBlock().getStdRegName().equals(BlockEnums.GRASS_BLOCK.getStdRegName())) {
                                continue;
                            }
                            if (y <= bestSpawnY) {
                                continue;
                            }
                            bestSpawnY = y;
                            bestSpawnX = chunkX * ServerChunkOld.CHUNK_SIZE + localX;
                            bestSpawnZ = chunkZ * ServerChunkOld.CHUNK_SIZE + localZ;
                        }
                    }
                }
            }
        }

        if (bestSpawnY < 0) {
            log.warn("世界:{} 在搜索范围内未找到草方块作为出生点", name);
            return;
        }

        int spawnY = bestSpawnY + 1;
        Pos spawnPos = Pos.of(bestSpawnX, spawnY, bestSpawnZ);
        this.defaultSpawnPos = spawnPos;

        ArchiveWorldIndexDto dto = new ArchiveWorldIndexDto();
        dto.setName(name);
        dto.setSeed(seed);
        dto.setTotalTick(totalTicks);
        dto.setTemplateStdRegName(template.getStdRegName().toString());
        dto.setSpawnX(bestSpawnX);
        dto.setSpawnY(spawnY);
        dto.setSpawnZ(bestSpawnZ);
        dto.setDefaultSpawnCreated(1);

        server.getArchiveService().getWorldService().saveWorldIndex(dto);
        log.info("世界:{} 的默认出生点已创建:{}", name, spawnPos);
    }


    public void generateChunkData(ServerChunkOld chunk) {
        if (terrainGenerator == null || generationContext == null) {
            return;
        }
        terrainGenerator.execute(chunk, generationContext);
    }
    
    public void generateChunkSynchronously(int chunkX, int chunkZ) {
        chunkManagerOld.generateChunkSynchronously(chunkX, chunkZ);
    }


    public int getChunkCount() {
        return chunkManagerOld.getChunkCount();
    }

    public int getBlockState(int x, int y, int z) {
        return chunkManagerOld.getBlockState(x, y, z);
    }

    public ServerChunkOld getChunk(int chunkX, int chunkZ) {
        return chunkManagerOld.getChunk(chunkX, chunkZ);
    }

    public void setBlockState(int x, int y, int z, int stateId) {
        chunkManagerOld.setBlockState(x, y, z, stateId);
    }

    public boolean canMoveTo(Vector3d position, double height) {
        return collisionManager.canMoveTo(position, height);
    }

    public boolean canMoveTo(BoundingBox box) {
        return collisionManager.canMoveTo(box);
    }

    public void addEntity(ServerEntity entity) {
        entityService.addEntity(entity);
    }

    public void removeEntity(ServerEntity entity) {
        entityService.removeEntity(entity);
    }

    public List<ServerEntity> getEntities() {
        return entityService.getEntities();
    }

    public void cleanup() {
        chunkManagerOld.cleanup();
    }
    
    public float getTimeOfDay() {
        return (float) (totalTicks % TICKS_PER_DAY) / TICKS_PER_DAY;
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

