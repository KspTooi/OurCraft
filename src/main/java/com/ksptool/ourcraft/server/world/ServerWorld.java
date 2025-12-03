package com.ksptool.ourcraft.server.world;

import com.ksptool.ourcraft.server.OurCraftServer;
import com.ksptool.ourcraft.server.archive.ArchiveService;
import com.ksptool.ourcraft.server.archive.model.ArchiveWorldIndexDto;
import com.ksptool.ourcraft.server.world.chunk.SimpleChunkManager;
import com.ksptool.ourcraft.server.world.chunk.SimpleServerChunk;
import com.ksptool.ourcraft.server.world.chunk.FlexChunkLeaseService;
import com.ksptool.ourcraft.server.world.chunk.FlexServerChunkService;
import com.ksptool.ourcraft.server.world.gen.NoiseGenerator;
import com.ksptool.ourcraft.server.world.save.SimpleRegionManager;
import com.ksptool.ourcraft.sharedcore.BoundingBox;
import com.ksptool.ourcraft.sharedcore.Registry;
import com.ksptool.ourcraft.sharedcore.utils.SimpleEventQueue;
import com.ksptool.ourcraft.sharedcore.enums.BlockEnums;
import com.ksptool.ourcraft.sharedcore.utils.position.Pos;
import com.ksptool.ourcraft.sharedcore.world.BlockState;
import com.ksptool.ourcraft.sharedcore.world.SharedWorld;
import com.ksptool.ourcraft.server.entity.ServerEntity;
import com.ksptool.ourcraft.server.entity.ServerPlayer;
import com.ksptool.ourcraft.server.event.ServerPlayerCameraInputEvent;
import com.ksptool.ourcraft.server.event.ServerPlayerInputEvent;
import com.ksptool.ourcraft.sharedcore.world.WorldTemplate;
import com.ksptool.ourcraft.server.archive.model.ArchiveWorldIndexVo;
import com.ksptool.ourcraft.sharedcore.world.gen.GenerationContext;
import com.ksptool.ourcraft.sharedcore.world.gen.TerrainGenerator;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.joml.Vector3d;
import org.joml.Vector3f;
import java.util.List;

/**
 * 服务端世界类，负责所有逻辑，不包含任何渲染相关代码
 */
@Getter
@Slf4j
public class ServerWorld implements SharedWorld {

    //该世界的默认玩家出生点
    @Setter
    private Pos defaultSpawnPos;

    private final OurCraftServer server;

    private final SimpleChunkManager scm;

    private final SimpleEntityService ses;

    private SimpleRegionManager srm;

    //服务端世界事件总线
    private final ServerWorldEventService sweb;

    //区块令牌服务
    private final FlexChunkLeaseService fcls;

    private final FlexServerChunkService fscs;

    private final ServerWorldPhysicsService swps;

    private final ServerWorldTimeService swts;

    @Setter
    private ArchiveService as;

    private final WorldTemplate template;

    private double timeAccumulator = 0.0;

    @Setter
    private String name;

    @Setter
    private String seed;

    @Getter
    private final GenerationContext generationContext;

    //地形生成器
    @Getter
    private final TerrainGenerator terrainGenerator;

    private final SimpleEventQueue seq;


    public ServerWorld(OurCraftServer server,WorldTemplate template) {
        this.fcls = new FlexChunkLeaseService(this);
        this.template = template;
        this.scm = new SimpleChunkManager(this);
        this.ses = new SimpleEntityService(this);
        this.swps = new ServerWorldPhysicsService(this);
        this.swts = new ServerWorldTimeService(this, 0L);
        this.seed = String.valueOf(System.currentTimeMillis());
        this.seq = SimpleEventQueue.getInstance();
        this.fscs = new FlexServerChunkService(server,this);
        this.server = server;

        //从注册表获取地形生成器
        TerrainGenerator terrainGenerator = Registry.getInstance().getTerrainGenerator(template.getTerrainGenerator());

        if(terrainGenerator == null){
            throw new IllegalArgumentException("无法初始化世界: " + template.getStdRegName() + " 因为地形生成器未注册: " + template.getTerrainGenerator());
        }

        this.terrainGenerator = terrainGenerator;
        var noiseGenerator = new NoiseGenerator(seed);
        this.generationContext = new GenerationContext(noiseGenerator, this, seed);
        this.sweb = new ServerWorldEventService();

        //注册事件处理器
        sweb.subscribe(ServerPlayerInputEvent.class, this::processPlayerInput);
        sweb.subscribe(ServerPlayerCameraInputEvent.class, this::processPlayerCameraInput);
    }

    /**
     * 从世界索引Vo中导入数据创建世界（用于恢复已有世界）
     * @param server 服务器实例
     * @param worldIndexVo 世界索引Vo
     */
    public ServerWorld(OurCraftServer server, ArchiveWorldIndexVo worldIndexVo) {
        if (worldIndexVo == null) {
            throw new IllegalArgumentException("世界索引Vo不能为空");
        }

        if (StringUtils.isBlank(worldIndexVo.getTemplateStdRegName())) {
            throw new IllegalArgumentException("世界模板标准注册名不能为空");
        }

        //从注册表获取世界模板
        WorldTemplate template = Registry.getInstance().getWorldTemplate(worldIndexVo.getTemplateStdRegName());
        if (template == null) {
            throw new IllegalArgumentException("无法初始化世界: " + worldIndexVo.getName() + " 因为世界模板未注册: " + worldIndexVo.getTemplateStdRegName());
        }

        this.fcls = new FlexChunkLeaseService(this);
        this.template = template;
        this.scm = new SimpleChunkManager(this);
        this.ses = new SimpleEntityService(this);
        this.swps = new ServerWorldPhysicsService(this);
        this.server = server;
        this.seed = worldIndexVo.getSeed();
        this.name = worldIndexVo.getName();
        long totalActions = worldIndexVo.getTotalTick() != null ? worldIndexVo.getTotalTick() : 0L;
        this.swts = new ServerWorldTimeService(this, totalActions);
        this.seq = SimpleEventQueue.getInstance();
        this.fscs = new FlexServerChunkService(server, this);

        //从注册表获取地形生成器
        TerrainGenerator terrainGenerator = Registry.getInstance().getTerrainGenerator(template.getTerrainGenerator());
        if (terrainGenerator == null) {
            throw new IllegalArgumentException("无法初始化世界: " + template.getStdRegName() + " 因为地形生成器未注册: " + template.getTerrainGenerator());
        }

        this.terrainGenerator = terrainGenerator;
        var noiseGenerator = new NoiseGenerator(seed);
        this.generationContext = new GenerationContext(noiseGenerator, this, seed);
        this.sweb = new ServerWorldEventService();

        //注册事件处理器
        sweb.subscribe(ServerPlayerInputEvent.class, this::processPlayerInput);
        sweb.subscribe(ServerPlayerCameraInputEvent.class, this::processPlayerCameraInput);

        //设置出生点（如果已创建）
        if (worldIndexVo.getDefaultSpawnCreated() != null && worldIndexVo.getDefaultSpawnCreated() == 1) {
            if (worldIndexVo.getDefaultSpawnX() != null && worldIndexVo.getDefaultSpawnY() != null && worldIndexVo.getDefaultSpawnZ() != null) {
                this.defaultSpawnPos = Pos.of(worldIndexVo.getDefaultSpawnX(), worldIndexVo.getDefaultSpawnY(), worldIndexVo.getDefaultSpawnZ());
            }
        }
    }


    public void init() {
        scm.init();
        //创建默认出生点
        createDefaultSpawn();
    }


    public void setSaveName(String saveName) {
        //this.saveName = saveName;
        this.scm.setSaveName(saveName);
        this.ses.setSaveName(saveName);
    }

    public void setSrm(SimpleRegionManager srm) {
        this.srm = srm;
        this.ses.setEntityRegionManager(srm);
    }


    /**
     * 新的update方法，由GameServer的主循环调用
     * 如果传入的deltaTime等于tickTime，则直接执行一次tick（固定时间步长模式）
     * 否则使用累加器模式处理可变时间增量（向后兼容）
     */
    public void update(float deltaTime, Vector3f playerPosition, Runnable playerTickCallback) {
        double tickTime = 1.0 / template.getActionPerSecond();
        
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

        scm.update(playerPosition);
        
        //eventQueue.offerS2C(new TimeUpdateEvent(getTimeOfDay()));
        
        float tickDelta = 1.0f / template.getActionPerSecond();
        
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
     * 执行世界逻辑
     * @param delta 距离上一帧经过的时间（秒）由SWEU传入
     */
    @Override
    public void action(double delta) {

        //时间服务动作(时间推进)
        swts.action(delta, this);

        //处理全部事件(通常包括输入,(网络线程会异步将玩家输入投入到队列中)这会应用Player的输入为他们的速度)
        sweb.action(delta, this);

        //物理服务动作(实体物理模拟,这会根据Player的速度模拟并更新他们的位置)
        swps.action(delta, this);

        //处理租约更新
        fcls.action(delta, this);

        //处理区块加载/卸载
        fscs.action(delta, this);

        //TODO:处理网络事件同步

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
                SimpleServerChunk chunk = getChunk(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }

                for (int localX = 0; localX < SimpleServerChunk.CHUNK_SIZE; localX++) {
                    for (int localZ = 0; localZ < SimpleServerChunk.CHUNK_SIZE; localZ++) {
                        for (int y = SimpleServerChunk.CHUNK_HEIGHT - 1; y >= 0; y--) {
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
                            bestSpawnX = chunkX * SimpleServerChunk.CHUNK_SIZE + localX;
                            bestSpawnZ = chunkZ * SimpleServerChunk.CHUNK_SIZE + localZ;
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
        dto.setTotalTick(swts.getTotalActions());
        dto.setTemplateStdRegName(template.getStdRegName().toString());
        dto.setSpawnX(bestSpawnX);
        dto.setSpawnY(spawnY);
        dto.setSpawnZ(bestSpawnZ);
        dto.setDefaultSpawnCreated(1);

        server.getArchiveService().getWorldService().saveWorldIndex(dto);
        log.info("世界:{} 的默认出生点已创建:{}", name, spawnPos);
    }

    /**
     * 处理Player输入事件(这些事件通常由网络线程投入到EventQueue)
     * @param e Player输入事件
     */
    private void processPlayerInput(ServerPlayerInputEvent e){

        var player = ses.getPlayerBySessionId(e.getSessionId());

        if (player == null) {
            log.warn("世界:{} 无法找到Player会话ID:{} 对应的PlayerEntity", name, e.getSessionId());
            return;
        }
        //应用玩家键盘输入事件(为Player增加速度以便在物理更新时生效)
        player.applyInput(e);
    }

    /**
     * 处理Player相机视角输入事件(这些事件通常由网络线程投入到EventQueue)
     * @param e Player相机视角输入事件
     */
    private void processPlayerCameraInput(ServerPlayerCameraInputEvent e){
        var player = ses.getPlayerBySessionId(e.getSessionId());

        if (player == null) {
            log.warn("世界:{} 无法找到Player会话ID:{} 对应的PlayerEntity", name, e.getSessionId());
            return;
        }
        //应用相机视角输入事件(为Player更新相机视角)
        player.updateCameraOrientation(e.getDeltaYaw(), e.getDeltaPitch());
    }



    public void generateChunkData(SimpleServerChunk chunk) {
        if (terrainGenerator == null || generationContext == null) {
            return;
        }
        terrainGenerator.execute(chunk, generationContext);
    }
    
    public void generateChunkSynchronously(int chunkX, int chunkZ) {
        scm.generateChunkSynchronously(chunkX, chunkZ);
    }


    public int getChunkCount() {
        return scm.getChunkCount();
    }

    public int getBlockState(int x, int y, int z) {
        return scm.getBlockState(x, y, z);
    }

    public SimpleServerChunk getChunk(int chunkX, int chunkZ) {
        return scm.getChunk(chunkX, chunkZ);
    }

    public void setBlockState(int x, int y, int z, int stateId) {
        scm.setBlockState(x, y, z, stateId);
    }

    public boolean canMoveTo(Vector3d position, double height) {
        return swps.canMoveTo(position, height);
    }

    public boolean canMoveTo(BoundingBox box) {
        return swps.canMoveTo(box);
    }

    public void addEntity(ServerEntity entity) {
        ses.addEntity(entity);
    }

    public void removeEntity(ServerEntity entity) {
        ses.removeEntity(entity);
    }

    public List<ServerEntity> getEntities() {
        return ses.getEntities();
    }

    public void cleanup() {
        scm.cleanup();
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

