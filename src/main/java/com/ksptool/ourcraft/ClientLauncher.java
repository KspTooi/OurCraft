package com.ksptool.ourcraft;

import com.ksptool.ourcraft.client.GameClient;
import com.ksptool.ourcraft.client.rendering.WorldRenderer;
import com.ksptool.ourcraft.client.world.ClientWorld;
import com.ksptool.ourcraft.server.OurCraftServerInstance;
import com.ksptool.ourcraft.server.entity.ServerPlayer;
import com.ksptool.ourcraft.client.entity.ClientPlayer;
import com.ksptool.ourcraft.server.world.ServerWorld;
import com.ksptool.ourcraft.sharedcore.world.Registry;
import com.ksptool.ourcraft.sharedcore.world.WorldTemplateOld;
import com.ksptool.ourcraft.server.world.save.RegionManager;
import com.ksptool.ourcraft.server.world.save.SaveManager;
import com.ksptool.ourcraft.server.world.save.WorldIndex;
import com.ksptool.ourcraft.server.world.save.WorldMetadata;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.UUID;

/**
 * 程序入口类，负责启动游戏并管理GameServer和GameClient的生命周期
 */
@Slf4j
public class ClientLauncher {
    private static GameClient gameClient;
    private static OurCraftServerInstance ourCraftServerInstance;
    private static ServerWorld serverWorld;
    private static ServerPlayer serverPlayer;
    private static ClientPlayer clientPlayer;
    private static String currentSaveName;
    private static String currentWorldName;

    public static void main(String[] args) {
        gameClient = new GameClient();
        gameClient.run();
    }

    /**
     * 启动游戏服务器（由GameClient调用）
     */
    public static void startGameServer(String saveName, String worldName) {

        if (StringUtils.isBlank(saveName) || StringUtils.isBlank(worldName)) {
            log.error("启动游戏服务器失败: 参数无效");
            return;
        }

        stopGameServer();

        log.info("启动游戏服务器: saveName={}, worldName={}", saveName, worldName);

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
            createNewWorld(saveName, worldName);
        } else {
            loadWorld(saveName, worldName);
        }

        if (serverWorld == null || serverPlayer == null) {
            log.error("启动游戏服务器失败: 世界或玩家创建失败");
            return;
        }

        ourCraftServerInstance = new OurCraftServerInstance();
        ourCraftServerInstance.init(serverWorld);
        ourCraftServerInstance.start();

        WorldTemplateOld template = serverWorld.getTemplate();
        ClientWorld clientWorld = new ClientWorld(template);
        WorldRenderer worldRenderer = new WorldRenderer(clientWorld);
        worldRenderer.init();

        // 创建 ClientPlayer（需要 ClientWorld）
        if (clientPlayer == null) {
            org.joml.Vector3f spawnPos = serverPlayer.getPosition();
            clientPlayer = new ClientPlayer(clientWorld);
            clientPlayer.setPosition(spawnPos);
            // 同步服务器玩家的朝向（ServerPlayer 有 @Getter，会自动生成 getYaw() 和 getPitch()）
            clientPlayer.setYaw(serverPlayer.getYaw());
            clientPlayer.setPitch(serverPlayer.getPitch());
            clientPlayer.initializeCamera();
        }

        gameClient.setGameWorld(clientWorld, worldRenderer, clientPlayer);

        currentSaveName = saveName;
        currentWorldName = worldName;

        log.info("游戏服务器启动完成");
    }

    /**
     * 停止游戏服务器（由GameClient调用）
     */
    public static void stopGameServer() {
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
        clientPlayer = null;
        currentSaveName = null;
        currentWorldName = null;
    }

    private static void createNewWorld(String saveName, String worldName) {
        log.info("创建新世界: saveName={}, worldName={}", saveName, worldName);

        SaveManager saveManager = SaveManager.getInstance();
        if (!saveManager.saveExists(saveName)) {
            if (!saveManager.createSave(saveName)) {
                log.error("创建存档失败: saveName={}", saveName);
                return;
            }
        }

        WorldTemplateOld template = Registry.getWorldTemplateOld("mycraft:overworld");
        if (template == null) {
            log.error("无法创建世界: 默认模板未找到");
            return;
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
        saveManager.savePalette(saveName, com.ksptool.ourcraft.sharedcore.world.GlobalPalette.getInstance());

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
        
        // ClientPlayer 将在 startGameServer 中创建，因为需要 ClientWorld
    }

    private static void loadWorld(String saveName, String worldName) {
        log.info("加载世界: saveName={}, worldName={}", saveName, worldName);

        SaveManager saveManager = SaveManager.getInstance();
        WorldIndex index = saveManager.loadWorldIndex(saveName);
        if (index == null || index.worlds == null) {
            log.error("加载世界失败: 无法读取世界索引 saveName={}", saveName);
            return;
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
            return;
        }

        com.ksptool.ourcraft.sharedcore.world.GlobalPalette palette = com.ksptool.ourcraft.sharedcore.world.GlobalPalette.getInstance();
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
                return;
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
        
        // ClientPlayer 将在 startGameServer 中创建，因为需要 ClientWorld
    }

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
        saveManager.savePalette(currentSaveName, com.ksptool.ourcraft.sharedcore.world.GlobalPalette.getInstance());
        
        serverWorld.saveAllDirtyData();
        
        if (serverPlayer != null) {
            saveManager.savePlayer(currentSaveName, serverPlayer.getUniqueId(), serverPlayer);
        }
    }
}
