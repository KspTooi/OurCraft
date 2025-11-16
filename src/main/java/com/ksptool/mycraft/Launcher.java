package com.ksptool.mycraft;

import com.ksptool.mycraft.client.GameClient;
import com.ksptool.mycraft.client.rendering.WorldRenderer;
import com.ksptool.mycraft.client.world.ClientWorld;
import com.ksptool.mycraft.server.entity.ServerPlayer;
import com.ksptool.mycraft.client.entity.ClientPlayer;
import com.ksptool.mycraft.server.GameServer;
import com.ksptool.mycraft.server.world.ServerWorld;
import com.ksptool.mycraft.world.Registry;
import com.ksptool.mycraft.world.WorldManager;
import com.ksptool.mycraft.world.WorldTemplate;
import com.ksptool.mycraft.world.save.RegionManager;
import com.ksptool.mycraft.world.save.SaveManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.UUID;

/**
 * 程序入口类，负责启动游戏并管理GameServer和GameClient的生命周期
 */
@Slf4j
public class Launcher {
    private static GameClient gameClient;
    private static GameServer gameServer;
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

        boolean isNewWorld = !WorldManager.getInstance().worldExists(saveName, worldName);
        
        if (isNewWorld) {
            createNewWorld(saveName, worldName);
        } else {
            loadWorld(saveName, worldName);
        }

        if (serverWorld == null || serverPlayer == null) {
            log.error("启动游戏服务器失败: 世界或玩家创建失败");
            return;
        }

        gameServer = new GameServer();
        gameServer.init(serverWorld, serverPlayer);
        gameServer.start();

        WorldTemplate template = serverWorld.getTemplate();
        ClientWorld clientWorld = new ClientWorld(template);
        WorldRenderer worldRenderer = new WorldRenderer(clientWorld);
        worldRenderer.init();

        gameClient.setGameWorld(clientWorld, worldRenderer, clientPlayer);

        currentSaveName = saveName;
        currentWorldName = worldName;

        log.info("游戏服务器启动完成");
    }

    /**
     * 停止游戏服务器（由GameClient调用）
     */
    public static void stopGameServer() {
        if (gameServer != null) {
            log.info("停止游戏服务器");
            gameServer.cleanup();
            gameServer = null;
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

        WorldTemplate template = Registry.getWorldTemplate("mycraft:overworld");
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

        WorldManager.getInstance().saveWorld(convertToWorld(serverWorld), saveName, worldName);

        float initialX = 8.0f;
        float initialZ = 8.0f;

        int playerChunkX = (int) Math.floor(initialX / com.ksptool.mycraft.server.world.ServerChunk.CHUNK_SIZE);
        int playerChunkZ = (int) Math.floor(initialZ / com.ksptool.mycraft.server.world.ServerChunk.CHUNK_SIZE);

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
        
        clientPlayer = new ClientPlayer();
        clientPlayer.getPosition().set(initialX, initialY, initialZ);
        clientPlayer.initializeCamera();
    }

    private static void loadWorld(String saveName, String worldName) {
        log.info("加载世界: saveName={}, worldName={}", saveName, worldName);

        com.ksptool.mycraft.world.World loadedWorld = WorldManager.getInstance().loadWorld(saveName, worldName);
        if (loadedWorld == null) {
            log.error("加载世界失败");
            return;
        }

        serverWorld = convertToServerWorld(loadedWorld);

        float initialX = 8.0f;
        float initialZ = 8.0f;

        int playerChunkX = (int) Math.floor(initialX / com.ksptool.mycraft.server.world.ServerChunk.CHUNK_SIZE);
        int playerChunkZ = (int) Math.floor(initialZ / com.ksptool.mycraft.server.world.ServerChunk.CHUNK_SIZE);

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

        com.ksptool.mycraft.world.save.PlayerIndex playerIndex = SaveManager.getInstance().loadPlayer(saveName, playerUUID);
        if (playerIndex != null) {
            serverPlayer.loadFromPlayerIndex(playerIndex);
            if (serverPlayer.getPosition().y > 0) {
                initialX = serverPlayer.getPosition().x;
                initialY = serverPlayer.getPosition().y;
                initialZ = serverPlayer.getPosition().z;
            }
        }
        
        clientPlayer = new ClientPlayer();
        clientPlayer.getPosition().set(initialX, initialY, initialZ);
        clientPlayer.initializeCamera();
    }

    private static void saveWorld() {
        if (serverWorld == null || currentSaveName == null || currentWorldName == null) {
            return;
        }

        log.info("保存世界: saveName={}, worldName={}", currentSaveName, currentWorldName);
        WorldManager.getInstance().saveWorld(convertToWorld(serverWorld), serverPlayer, currentSaveName, currentWorldName);
    }

    /**
     * 临时转换方法：将ServerWorld转换为World（用于兼容现有代码）
     * 注意：这个方法只是创建一个包装器，实际的区块数据仍然在ServerWorld中
     * TODO: 未来应该修改WorldManager以直接接受ServerWorld
     */
    private static com.ksptool.mycraft.world.World convertToWorld(ServerWorld serverWorld) {
        if (serverWorld == null) {
            return null;
        }
        WorldTemplate template = serverWorld.getTemplate();
        com.ksptool.mycraft.world.World world = new com.ksptool.mycraft.world.World(template);
        world.setWorldName(serverWorld.getWorldName());
        world.setSeed(serverWorld.getSeed());
        world.setGameTime(serverWorld.getGameTime());
        world.setSaveName(serverWorld.getSaveName());
        world.setRegionManager(serverWorld.getRegionManager());
        world.setEntityRegionManager(serverWorld.getEntityRegionManager());
        world.init();
        
        com.ksptool.mycraft.world.ChunkManager worldChunkManager = world.getChunkManager();
        com.ksptool.mycraft.world.ChunkManager serverChunkManager = serverWorld.getChunkManager();
        
        for (java.util.Map.Entry<Long, com.ksptool.mycraft.server.world.ServerChunk> entry : serverChunkManager.getChunks().entrySet()) {
            worldChunkManager.getChunks().put(entry.getKey(), entry.getValue());
        }
        
        return world;
    }

    /**
     * 临时转换方法：将World转换为ServerWorld
     * 注意：这个方法复制区块数据以确保数据一致性
     * TODO: 未来应该直接加载为ServerWorld
     */
    private static ServerWorld convertToServerWorld(com.ksptool.mycraft.world.World world) {
        if (world == null) {
            return null;
        }
        ServerWorld serverWorld = new ServerWorld(world.getTemplate());
        serverWorld.setWorldName(world.getWorldName());
        serverWorld.setSeed(world.getSeed());
        serverWorld.setGameTime(world.getGameTime());
        serverWorld.setSaveName(world.getSaveName());
        serverWorld.setRegionManager(world.getRegionManager());
        serverWorld.setEntityRegionManager(world.getEntityRegionManager());
        serverWorld.init();
        
        com.ksptool.mycraft.world.ChunkManager worldChunkManager = world.getChunkManager();
        com.ksptool.mycraft.world.ChunkManager serverChunkManager = serverWorld.getChunkManager();
        
        for (java.util.Map.Entry<Long, com.ksptool.mycraft.server.world.ServerChunk> entry : worldChunkManager.getChunks().entrySet()) {
            serverChunkManager.getChunks().put(entry.getKey(), entry.getValue());
        }
        
        return serverWorld;
    }
}
