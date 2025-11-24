package com.ksptool.ourcraft.server.world.save;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ksptool.ourcraft.sharedcore.GlobalPalette;
import com.ksptool.ourcraft.sharedcore.Registry;
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

/**
 * 存档管理器，负责管理存档文件夹结构和世界/玩家数据的保存与加载
 */
public class SaveManager {
    private static final Logger logger = LoggerFactory.getLogger(SaveManager.class);
    private static final String SAVES_DIR = "saves";
    private static final String WORLD_INDEX_FILE = "world.index";
    private static SaveManager instance;
    private final Gson gson;

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
    
    public void savePalette(String saveName, GlobalPalette palette) {
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
    
    public boolean loadPalette(String saveName, GlobalPalette palette) {
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
            Registry registry = Registry.getInstance();
            
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

