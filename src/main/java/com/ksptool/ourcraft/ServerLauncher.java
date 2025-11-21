package com.ksptool.ourcraft;

import com.ksptool.ourcraft.server.OurCraftServerInstance;
import com.ksptool.ourcraft.server.entity.ServerPlayer;
import com.ksptool.ourcraft.server.world.ServerWorld;
import com.ksptool.ourcraft.server.world.save.RegionManager;
import com.ksptool.ourcraft.server.world.save.SaveManager;
import com.ksptool.ourcraft.server.world.save.WorldIndex;
import com.ksptool.ourcraft.server.world.save.WorldMetadata;
import com.ksptool.ourcraft.sharedcore.blocks.inner.SharedBlock;
import com.ksptool.ourcraft.sharedcore.world.GlobalPalette;
import com.ksptool.ourcraft.sharedcore.world.Registry;
import com.ksptool.ourcraft.sharedcore.world.WorldTemplateOld;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import java.io.File;
import java.util.UUID;

/**
 * 独立服务器启动类，用于启动专用服务器（不依赖客户端）
 * 用法: java -jar our-craft.jar server <saveName> <worldName>
 */
@Slf4j
public class ServerLauncher {
    
    private static OurCraftServerInstance ourCraftServerInstance;
    private static ServerWorld serverWorld;
    private static ServerPlayer serverPlayer;
    private static String currentSaveName;
    private static String currentWorldName;
    private static volatile boolean running = true;
    
    public static void main(String[] args) {
        // 注册关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("收到关闭信号，正在关闭服务器...");
            stopServer();
        }));
        
        // 解析命令行参数
        String saveName;
        String worldName;
        
        if (args.length >= 3 && "server".equals(args[0])) {
            saveName = args[1];
            worldName = args[2];
            
            if (StringUtils.isBlank(saveName) || StringUtils.isBlank(worldName)) {
                log.warn("参数无效，使用默认值: saveName=our_craft, worldName=earth_like");
                saveName = "our_craft";
                worldName = "earth_like";
            }
        } else {
            log.warn("参数格式不正确，使用默认值: saveName=our_craft, worldName=earth_like");
            log.warn("正确用法: java -jar our-craft.jar server <saveName> <worldName>");
            saveName = "our_craft";
            worldName = "earth_like";
        }
        
        log.info("========================================");
        log.info("OurCraft 专用服务器启动中...");
        log.info("存档名称: {}", saveName);
        log.info("世界名称: {}", worldName);
        log.info("========================================");
        
        // 初始化基础系统
        initializeSystems();
        
        // 启动服务器
        if (!startServer(saveName, worldName)) {
            log.error("服务器启动失败");
            System.exit(1);
        }
        
        log.info("服务器已启动，等待客户端连接...");
        log.info("按 Ctrl+C 停止服务器");
        
        // 保持主线程运行
        try {
            while (running) {
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("主线程被中断");
        }
        
        stopServer();
        log.info("服务器已关闭");
    }
    
    /**
     * 初始化基础系统
     */
    private static void initializeSystems() {
        log.info("初始化基础系统...");
        
        // 注册默认世界模板
        WorldTemplateOld overworldTemplateOld = WorldTemplateOld.builder()
            .templateId("mycraft:overworld")
            .ticksPerSecond(20)
            .gravity(-9.8f)
            .build();
        Registry.registerWorldTemplateOld(overworldTemplateOld);
        
        // 注册方块
        SharedBlock.registerBlocks();
        
        // 初始化全局调色板
        GlobalPalette.getInstance().bake();
        
        log.info("基础系统初始化完成");
    }
    
    /**
     * 启动服务器
     */
    private static boolean startServer(String saveName, String worldName) {
        currentSaveName = saveName;
        currentWorldName = worldName;
        
        log.info("加载世界: saveName={}, worldName={}", saveName, worldName);
        
        SaveManager saveManager = SaveManager.getInstance();
        WorldIndex index = saveManager.loadWorldIndex(saveName);
        boolean isNewWorld = true;
        
        if (index != null && index.worlds != null) {
            for (WorldMetadata metadata : index.worlds) {
                if (metadata != null && worldName.equals(metadata.name)) {
                    isNewWorld = false;
                    break;
                }
            }
        }
        
        if (isNewWorld) {
            if (!createNewWorld(saveName, worldName)) {
                return false;
            }
        } else {
            if (!loadWorld(saveName, worldName)) {
                return false;
            }
        }
        
        if (serverWorld == null || serverPlayer == null) {
            log.error("启动服务器失败: 世界或玩家创建失败");
            return false;
        }
        
        // 启动GameServer
        ourCraftServerInstance = new OurCraftServerInstance();
        ourCraftServerInstance.init(serverWorld);
        ourCraftServerInstance.start();
        
        log.info("服务器启动完成");
        return true;
    }
    
    /**
     * 创建新世界
     */
    private static boolean createNewWorld(String saveName, String worldName) {
        log.info("创建新世界: saveName={}, worldName={}", saveName, worldName);
        
        SaveManager saveManager = SaveManager.getInstance();
        if (!saveManager.saveExists(saveName)) {
            if (!saveManager.createSave(saveName)) {
                log.error("创建存档失败: saveName={}", saveName);
                return false;
            }
        }
        
        WorldTemplateOld template = Registry.getWorldTemplateOld("mycraft:overworld");
        if (template == null) {
            log.error("无法创建世界: 默认模板未找到");
            return false;
        }
        
        serverWorld = new ServerWorld(template);
        serverWorld.setWorldName(worldName);
        serverWorld.setSeed(System.currentTimeMillis());
        serverWorld.setSaveName(saveName);
        
        File chunksDir = saveManager.getWorldChunkDir(saveName, worldName);
        if (chunksDir != null) {
            RegionManager regionManager = new RegionManager(chunksDir, ".sca", "SCAF");
            serverWorld.setRegionManager(regionManager);
        }
        
        File entityDir = saveManager.getWorldEntityDir(saveName, worldName);
        if (entityDir != null) {
            RegionManager entityRegionManager = new RegionManager(entityDir, ".sce", "SCEF");
            serverWorld.setEntityRegionManager(entityRegionManager);
        }
        
        serverWorld.init();
        
        WorldIndex index = saveManager.loadWorldIndex(saveName);
        if (index == null) {
            index = new WorldIndex();
        }
        WorldMetadata metadata = new WorldMetadata();
        metadata.name = worldName;
        metadata.seed = serverWorld.getSeed();
        metadata.worldTime = serverWorld.getGameTime();
        metadata.templateId = serverWorld.getTemplate().getTemplateId();
        index.worlds.add(metadata);
        saveManager.saveWorldIndex(saveName, index);
        saveManager.savePalette(saveName, GlobalPalette.getInstance());
        
        float initialX = 8.0f;
        float initialZ = 8.0f;
        
        int playerChunkX = (int) Math.floor(initialX / com.ksptool.ourcraft.server.world.ServerChunk.CHUNK_SIZE);
        int playerChunkZ = (int) Math.floor(initialZ / com.ksptool.ourcraft.server.world.ServerChunk.CHUNK_SIZE);
        
        for (int x = playerChunkX - 2; x <= playerChunkX + 2; x++) {
            for (int z = playerChunkZ - 2; z <= playerChunkZ + 2; z++) {
                serverWorld.generateChunkSynchronously(x, z);
            }
        }
        
        int groundHeight = serverWorld.getHeightAt((int) initialX, (int) initialZ);
        float initialY = groundHeight + 1.0f;
        
        serverPlayer = new ServerPlayer(serverWorld);
        serverPlayer.getPosition().set(initialX, initialY, initialZ);
        serverWorld.addEntity(serverPlayer);
        
        return true;
    }
    
    /**
     * 加载现有世界
     */
    private static boolean loadWorld(String saveName, String worldName) {
        log.info("加载世界: saveName={}, worldName={}", saveName, worldName);
        
        SaveManager saveManager = SaveManager.getInstance();
        WorldIndex index = saveManager.loadWorldIndex(saveName);
        if (index == null || index.worlds == null) {
            log.error("加载世界失败: 无法读取世界索引 saveName={}", saveName);
            return false;
        }
        
        WorldMetadata metadata = null;
        for (WorldMetadata m : index.worlds) {
            if (m != null && worldName.equals(m.name)) {
                metadata = m;
                break;
            }
        }
        
        if (metadata == null) {
            log.error("加载世界失败: 世界不存在 saveName={}, worldName={}", saveName, worldName);
            return false;
        }
        
        GlobalPalette palette = GlobalPalette.getInstance();
        if (!palette.isBaked()) {
            if (!saveManager.loadPalette(saveName, palette)) {
                log.debug("调色板文件不存在，使用默认调色板");
                palette.bake();
            } else {
                log.debug("已加载调色板");
            }
        }
        
        WorldTemplateOld template = Registry.getWorldTemplateOld(metadata.templateId);
        if (template == null) {
            log.warn("找不到世界模板 '{}', 使用默认模板", metadata.templateId);
            template = Registry.getDefaultTemplate();
            if (template == null) {
                log.error("无法加载世界: 默认模板未找到");
                return false;
            }
        }
        
        serverWorld = new ServerWorld(template);
        serverWorld.setWorldName(worldName);
        serverWorld.setSeed(metadata.seed);
        serverWorld.setGameTime(metadata.worldTime);
        serverWorld.setSaveName(saveName);
        
        File chunksDir = saveManager.getWorldChunkDir(saveName, worldName);
        if (chunksDir != null) {
            RegionManager regionManager = new RegionManager(chunksDir, ".sca", "SCAF");
            serverWorld.setRegionManager(regionManager);
            log.debug("已设置区块区域管理器");
        }
        
        File entityDir = saveManager.getWorldEntityDir(saveName, worldName);
        if (entityDir != null) {
            RegionManager entityRegionManager = new RegionManager(entityDir, ".sce", "SCEF");
            serverWorld.setEntityRegionManager(entityRegionManager);
            log.debug("已设置实体区域管理器");
        }
        
        serverWorld.init();
        
        float initialX = 8.0f;
        float initialZ = 8.0f;
        
        int playerChunkX = (int) Math.floor(initialX / com.ksptool.ourcraft.server.world.ServerChunk.CHUNK_SIZE);
        int playerChunkZ = (int) Math.floor(initialZ / com.ksptool.ourcraft.server.world.ServerChunk.CHUNK_SIZE);
        
        for (int x = playerChunkX - 2; x <= playerChunkX + 2; x++) {
            for (int z = playerChunkZ - 2; z <= playerChunkZ + 2; z++) {
                serverWorld.generateChunkSynchronously(x, z);
            }
        }
        
        int groundHeight = serverWorld.getHeightAt((int) initialX, (int) initialZ);
        float initialY = groundHeight + 1.0f;
        
        UUID playerUUID = SaveManager.getInstance().findFirstPlayerUUID(saveName);
        if (playerUUID == null) {
            playerUUID = UUID.randomUUID();
        }
        
        serverPlayer = new ServerPlayer(serverWorld, playerUUID);
        serverPlayer.getPosition().set(initialX, initialY, initialZ);
        serverWorld.addEntity(serverPlayer);
        
        com.ksptool.ourcraft.server.world.save.PlayerIndex playerIndex = SaveManager.getInstance().loadPlayer(saveName, playerUUID);
        if (playerIndex != null) {
            serverPlayer.loadFromPlayerIndex(playerIndex);
            if (serverPlayer.getPosition().y > 0) {
                initialX = serverPlayer.getPosition().x;
                initialY = serverPlayer.getPosition().y;
                initialZ = serverPlayer.getPosition().z;
            }
        }
        
        return true;
    }
    
    /**
     * 停止服务器
     */
    private static void stopServer() {
        running = false;
        
        if (ourCraftServerInstance != null) {
            log.info("停止游戏服务器");
            ourCraftServerInstance.cleanup();
            ourCraftServerInstance = null;
        }
        
        if (serverWorld != null && currentSaveName != null && currentWorldName != null) {
            saveWorld();
        }
        
        if (serverWorld != null) {
            serverWorld.cleanup();
            serverWorld = null;
        }
        
        serverPlayer = null;
        currentSaveName = null;
        currentWorldName = null;
    }
    
    /**
     * 保存世界
     */
    private static void saveWorld() {
        if (serverWorld == null || currentSaveName == null || currentWorldName == null) {
            return;
        }
        
        log.info("保存世界: saveName={}, worldName={}", currentSaveName, currentWorldName);
        
        SaveManager saveManager = SaveManager.getInstance();
        WorldIndex index = saveManager.loadWorldIndex(currentSaveName);
        if (index == null) {
            index = new WorldIndex();
        }
        
        WorldMetadata metadata = null;
        for (WorldMetadata m : index.worlds) {
            if (m != null && currentWorldName.equals(m.name)) {
                metadata = m;
                break;
            }
        }
        
        if (metadata == null) {
            metadata = new WorldMetadata();
            metadata.name = currentWorldName;
            index.worlds.add(metadata);
        }
        
        metadata.seed = serverWorld.getSeed();
        metadata.worldTime = serverWorld.getGameTime();
        metadata.templateId = serverWorld.getTemplate().getTemplateId();
        
        saveManager.saveWorldIndex(currentSaveName, index);
        saveManager.savePalette(currentSaveName, GlobalPalette.getInstance());
        
        serverWorld.saveAllDirtyData();
        
        if (serverPlayer != null) {
            saveManager.savePlayer(currentSaveName, serverPlayer.getUniqueId(), serverPlayer);
        }
        
        log.info("世界保存完成");
    }
    
}

