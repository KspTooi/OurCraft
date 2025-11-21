package com.ksptool.ourcraft.server.world.save;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ksptool.ourcraft.sharedcore.blocks.inner.SharedBlock;
import com.ksptool.ourcraft.sharedcore.world.BlockState;
import com.ksptool.ourcraft.sharedcore.world.properties.BlockProperty;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 存档管理器，负责管理存档文件夹结构和世界/玩家数据的保存与加载
 */
public class SaveManager {
    private static final Logger logger = LoggerFactory.getLogger(SaveManager.class);
    private static final String SAVES_DIR = "saves";
    private static final String WORLD_INDEX_FILE = "world.index";
    private static final String PLAYERS_DIR = "players";
    private static final String PLAYER_UUID_MAP_FILE = "player_uuids.json";
    private static SaveManager instance;
    private final Gson gson;
    private final Map<String, Map<String, UUID>> savePlayerUuidCache = new ConcurrentHashMap<>();

    private SaveManager() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        File savesDir = new File(SAVES_DIR);
        if (!savesDir.exists()) {
            savesDir.mkdirs();
        }
    }

    public static SaveManager getInstance() {
        if (instance == null) {
            instance = new SaveManager();
        }
        return instance;
    }

    private static class PlayerUUIDMap {
        Map<String, String> nameToUuid = new ConcurrentHashMap<>();
    }

    public UUID getOrCreatePlayerUUID(String saveName, String playerName) {
        if (StringUtils.isBlank(saveName) || StringUtils.isBlank(playerName)) {
            return UUID.randomUUID();
        }

        Map<String, UUID> uuidMap = savePlayerUuidCache.computeIfAbsent(saveName, this::loadPlayerUUIDs);
        UUID playerUUID = uuidMap.get(playerName);

        if (playerUUID == null) {
            playerUUID = UUID.randomUUID();
            uuidMap.put(playerName, playerUUID);
            savePlayerUUIDs(saveName, uuidMap);
            logger.info("为新玩家 '{}' 创建了新的 UUID: {}", playerName, playerUUID);
        }

        return playerUUID;
    }

    private Map<String, UUID> loadPlayerUUIDs(String saveName) {
        Map<String, UUID> result = new ConcurrentHashMap<>();
        File saveDir = new File(SAVES_DIR, saveName);
        if (!saveDir.exists()) {
            return result;
        }

        File uuidFile = new File(saveDir, PLAYER_UUID_MAP_FILE);
        if (!uuidFile.exists()) {
            return result;
        }

        try (FileReader reader = new FileReader(uuidFile)) {
            PlayerUUIDMap uuidMapData = gson.fromJson(reader, PlayerUUIDMap.class);
            if (uuidMapData != null && uuidMapData.nameToUuid != null) {
                for (Map.Entry<String, String> entry : uuidMapData.nameToUuid.entrySet()) {
                    result.put(entry.getKey(), UUID.fromString(entry.getValue()));
                }
            }
            logger.debug("成功加载 {} 的玩家UUID映射", saveName);
        } catch (IOException e) {
            logger.error("加载玩家UUID映射失败: saveName={}", saveName, e);
        }
        return result;
    }

    private void savePlayerUUIDs(String saveName, Map<String, UUID> uuidMap) {
        File saveDir = new File(SAVES_DIR, saveName);
        if (!saveDir.exists()) {
            if (!saveDir.mkdirs()) {
                logger.error("无法创建存档目录: {}", saveName);
                return;
            }
        }

        File uuidFile = new File(saveDir, PLAYER_UUID_MAP_FILE);
        PlayerUUIDMap uuidMapData = new PlayerUUIDMap();
        for (Map.Entry<String, UUID> entry : uuidMap.entrySet()) {
            uuidMapData.nameToUuid.put(entry.getKey(), entry.getValue().toString());
        }

        try (FileWriter writer = new FileWriter(uuidFile)) {
            gson.toJson(uuidMapData, writer);
            logger.debug("成功保存 {} 的玩家UUID映射", saveName);
        } catch (IOException e) {
            logger.error("保存玩家UUID映射失败: saveName={}", saveName, e);
        }
    }

    public List<String> getSaveList() {
        List<String> saves = new ArrayList<>();
        File savesDir = new File(SAVES_DIR);
        if (!savesDir.exists()) {
            return saves;
        }

        File[] files = savesDir.listFiles();
        if (files == null) {
            return saves;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                File worldIndexFile = new File(file, WORLD_INDEX_FILE);
                if (worldIndexFile.exists()) {
                    saves.add(file.getName());
                }
            }
        }

        return saves;
    }

    public boolean createSave(String saveName) {
        if (StringUtils.isBlank(saveName)) {
            return false;
        }

        File saveDir = new File(SAVES_DIR, saveName);
        if (saveDir.exists()) {
            return false;
        }

        if (!saveDir.mkdirs()) {
            return false;
        }

        File playersDir = new File(saveDir, PLAYERS_DIR);
        playersDir.mkdirs();

        WorldIndex index = new WorldIndex();
        return saveWorldIndex(saveName, index);
    }

    public boolean saveExists(String saveName) {
        if (StringUtils.isBlank(saveName)) {
            return false;
        }

        File saveDir = new File(SAVES_DIR, saveName);
        if (!saveDir.exists()) {
            return false;
        }

        File worldIndexFile = new File(saveDir, WORLD_INDEX_FILE);
        return worldIndexFile.exists();
    }

    public WorldIndex loadWorldIndex(String saveName) {
        if (StringUtils.isBlank(saveName)) {
            return null;
        }

        File saveDir = new File(SAVES_DIR, saveName);
        if (!saveDir.exists()) {
            return null;
        }

        File worldIndexFile = new File(saveDir, WORLD_INDEX_FILE);
        if (!worldIndexFile.exists()) {
            return new WorldIndex();
        }

        try (FileReader reader = new FileReader(worldIndexFile)) {
            return gson.fromJson(reader, WorldIndex.class);
        } catch (IOException e) {
            logger.error("加载世界索引失败: {}", saveName, e);
            return new WorldIndex();
        }
    }

    public boolean saveWorldIndex(String saveName, WorldIndex index) {
        if (StringUtils.isBlank(saveName) || index == null) {
            return false;
        }

        File saveDir = new File(SAVES_DIR, saveName);
        if (!saveDir.exists()) {
            if (!saveDir.mkdirs()) {
                return false;
            }
        }

        File worldIndexFile = new File(saveDir, WORLD_INDEX_FILE);
        try (FileWriter writer = new FileWriter(worldIndexFile)) {
            gson.toJson(index, writer);
            return true;
        } catch (IOException e) {
            logger.error("保存世界索引失败: {}", saveName, e);
            return false;
        }
    }

    public void savePlayer(String saveName, UUID playerUUID, com.ksptool.ourcraft.server.entity.ServerPlayer player) {
        if (StringUtils.isBlank(saveName) || playerUUID == null || player == null) {
            return;
        }

        File saveDir = new File(SAVES_DIR, saveName);
        if (!saveDir.exists()) {
            return;
        }

        File playersDir = new File(saveDir, PLAYERS_DIR);
        if (!playersDir.exists()) {
            playersDir.mkdirs();
        }

        PlayerIndex playerIndex = new PlayerIndex();
        playerIndex.uuid = playerUUID;
        playerIndex.posX = player.getPosition().x;
        playerIndex.posY = player.getPosition().y;
        playerIndex.posZ = player.getPosition().z;
        playerIndex.yaw = player.getYaw();
        playerIndex.pitch = player.getPitch();
        playerIndex.health = player.getHealth();
        playerIndex.selectedSlot = player.getInventory().getSelectedSlot();

        com.ksptool.ourcraft.sharedcore.item.ItemStack[] hotbar = player.getInventory().getHotbar();
        for (com.ksptool.ourcraft.sharedcore.item.ItemStack stack : hotbar) {
            if (stack != null && !stack.isEmpty()) {
                ItemStackData stackData = new ItemStackData(
                    stack.getItem() != null ? stack.getItem().getId() : null,
                    stack.getCount()
                );
                playerIndex.hotbar.add(stackData);
            } else {
                playerIndex.hotbar.add(new ItemStackData(null, null));
            }
        }

        File playerFile = new File(playersDir, playerUUID.toString() + ".index");
        try (FileWriter writer = new FileWriter(playerFile)) {
            gson.toJson(playerIndex, writer);
            logger.debug("保存玩家数据成功: UUID={}", playerUUID);
        } catch (IOException e) {
            logger.error("保存玩家数据失败: UUID={}", playerUUID, e);
        }
    }

    public UUID findFirstPlayerUUID(String saveName) {
        if (StringUtils.isBlank(saveName)) {
            return null;
        }

        File saveDir = new File(SAVES_DIR, saveName);
        if (!saveDir.exists()) {
            return null;
        }

        File playersDir = new File(saveDir, PLAYERS_DIR);
        if (!playersDir.exists()) {
            return null;
        }

        File[] files = playersDir.listFiles((dir, name) -> name.endsWith(".index"));
        if (files == null || files.length == 0) {
            return null;
        }

        String fileName = files[0].getName();
        String uuidStr = fileName.substring(0, fileName.length() - 6);
        try {
            return UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public PlayerIndex loadPlayer(String saveName, UUID playerUUID) {
        if (StringUtils.isBlank(saveName) || playerUUID == null) {
            return null;
        }

        File saveDir = new File(SAVES_DIR, saveName);
        if (!saveDir.exists()) {
            return null;
        }

        File playersDir = new File(saveDir, PLAYERS_DIR);
        if (!playersDir.exists()) {
            return null;
        }

        File playerFile = new File(playersDir, playerUUID.toString() + ".index");
        if (!playerFile.exists()) {
            return null;
        }

        try (FileReader reader = new FileReader(playerFile)) {
            PlayerIndex playerIndex = gson.fromJson(reader, PlayerIndex.class);
            logger.debug("加载玩家数据成功: UUID={}", playerUUID);
            return playerIndex;
        } catch (IOException e) {
            logger.error("加载玩家数据失败: UUID={}", playerUUID, e);
            return null;
        }
    }

    public File getWorldChunkDir(String saveName, String worldName) {
        if (StringUtils.isBlank(saveName) || StringUtils.isBlank(worldName)) {
            return null;
        }

        File saveDir = new File(SAVES_DIR, saveName);
        if (!saveDir.exists()) {
            return null;
        }

        File chunkDir = new File(saveDir, worldName + "_chunk");
        if (!chunkDir.exists()) {
            chunkDir.mkdirs();
        }

        return chunkDir;
    }

    public File getWorldEntityDir(String saveName, String worldName) {
        if (StringUtils.isBlank(saveName) || StringUtils.isBlank(worldName)) {
            return null;
        }

        File saveDir = new File(SAVES_DIR, saveName);
        if (!saveDir.exists()) {
            return null;
        }

        File entityDir = new File(saveDir, worldName + "_entity");
        if (!entityDir.exists()) {
            entityDir.mkdirs();
        }

        return entityDir;
    }
    
    public void savePalette(String saveName, com.ksptool.ourcraft.sharedcore.world.GlobalPalette palette) {
        if (StringUtils.isBlank(saveName) || palette == null || !palette.isBaked()) {
            return;
        }
        
        File saveDir = new File(SAVES_DIR, saveName);
        if (!saveDir.exists()) {
            return;
        }
        
        File paletteFile = new File(saveDir, "palette.json");
        PaletteIndex paletteIndex = new PaletteIndex();
        
        for (int i = 0; i < palette.getStateCount(); i++) {
            BlockState state = palette.getState(i);
            BlockStateData stateData = new BlockStateData();
            stateData.setStdRegName(state.getSharedBlock().getStdRegName().getValue());
            
            java.util.Map<String, String> propsMap = new java.util.HashMap<>();
            for (java.util.Map.Entry<BlockProperty<?>, Comparable<?>> entry : state.getProperties().entrySet()) {
                propsMap.put(entry.getKey().getName(), entry.getValue().toString());
            }
            stateData.setProperties(propsMap);
            paletteIndex.states.add(stateData);
        }
        
        try (FileWriter writer = new FileWriter(paletteFile)) {
            gson.toJson(paletteIndex, writer);
            logger.debug("保存调色板成功: 状态数={}", paletteIndex.states.size());
        } catch (IOException e) {
            logger.error("保存调色板失败: {}", saveName, e);
        }
    }
    
    public boolean loadPalette(String saveName, com.ksptool.ourcraft.sharedcore.world.GlobalPalette palette) {
        if (StringUtils.isBlank(saveName) || palette == null) {
            return false;
        }
        
        File saveDir = new File(SAVES_DIR, saveName);
        if (!saveDir.exists()) {
            return false;
        }
        
        File paletteFile = new File(saveDir, "palette.json");
        if (!paletteFile.exists()) {
            return false;
        }
        
        try (FileReader reader = new FileReader(paletteFile)) {
            PaletteIndex paletteIndex = gson.fromJson(reader, PaletteIndex.class);
            if (paletteIndex == null || paletteIndex.states == null) {
                return false;
            }
            
            palette.clear();
            com.ksptool.ourcraft.sharedcore.world.Registry registry = com.ksptool.ourcraft.sharedcore.world.Registry.getInstance();
            
            for (BlockStateData stateData : paletteIndex.states) {
                if (stateData == null || StringUtils.isBlank(stateData.getStdRegName())) {
                    continue;
                }
                
                SharedBlock sharedBlock = registry.getBlock(stateData.getStdRegName());
                if (sharedBlock == null) {
                    continue;
                }
                
                java.util.Map<BlockProperty<?>, Comparable<?>> properties = new java.util.HashMap<>();
                if (stateData.getProperties() != null) {
                    for (java.util.Map.Entry<String, String> propEntry : stateData.getProperties().entrySet()) {
                        String propName = propEntry.getKey();
                        String propValueStr = propEntry.getValue();
                        
                        for (BlockProperty<?> property : sharedBlock.getProperties()) {
                            if (property.getName().equals(propName)) {
                                Comparable<?> value = parsePropertyValue(property, propValueStr);
                                if (value != null) {
                                    properties.put(property, value);
                                }
                                break;
                            }
                        }
                    }
                }
                
                BlockState state = new BlockState(sharedBlock, properties);
                palette.getStateList().add(state);
                palette.getStateToId().put(state, palette.getStateList().size() - 1);
            }
            
            palette.setBaked(true);
            logger.debug("加载调色板成功: 状态数={}", paletteIndex.states.size());
            return true;
        } catch (IOException e) {
            logger.error("加载调色板失败: {}", saveName, e);
            return false;
        }
    }
    
    @SuppressWarnings("unchecked")
    private Comparable<?> parsePropertyValue(BlockProperty<?> property, String valueStr) {
        java.util.Collection<?> allowedValues = property.getAllowedValues();
        if (allowedValues.isEmpty()) {
            return null;
        }
        
        Object sample = allowedValues.iterator().next();
        if (sample instanceof Enum) {
            for (Object val : allowedValues) {
                if (val.toString().equals(valueStr)) {
                    return (Comparable<?>) val;
                }
            }
        } else if (sample instanceof Boolean) {
            return Boolean.parseBoolean(valueStr);
        } else if (sample instanceof Integer) {
            return Integer.parseInt(valueStr);
        }
        
        return null;
    }
}

