package com.ksptool.mycraft.world.save;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 区域管理器，负责管理区域文件的创建、打开和缓存
 */
public class RegionManager {
    private static final Logger logger = LoggerFactory.getLogger(RegionManager.class);
    private static final int REGION_SIZE = 40;
    private final File baseDir;
    private final String fileExtension;
    private final String magicNumber;
    private final Map<String, RegionFile> openRegions;
    
    public RegionManager(File baseDir, String fileExtension, String magicNumber) {
        this.baseDir = baseDir;
        this.fileExtension = fileExtension;
        this.magicNumber = magicNumber;
        this.openRegions = new ConcurrentHashMap<>();
    }
    
    public RegionFile getRegionFile(int regionX, int regionZ) {
        String key = regionX + "." + regionZ;
        return openRegions.computeIfAbsent(key, k -> {
            File regionFile = new File(baseDir, "r." + regionX + "." + regionZ + fileExtension);
            return new RegionFile(regionFile, magicNumber);
        });
    }
    
    public static int getRegionX(int chunkX) {
        return chunkX >= 0 ? chunkX / REGION_SIZE : (chunkX + 1) / REGION_SIZE - 1;
    }
    
    public static int getRegionZ(int chunkZ) {
        return chunkZ >= 0 ? chunkZ / REGION_SIZE : (chunkZ + 1) / REGION_SIZE - 1;
    }
    
    public static int getLocalChunkX(int chunkX) {
        int regionX = getRegionX(chunkX);
        return chunkX - (regionX * REGION_SIZE);
    }
    
    public static int getLocalChunkZ(int chunkZ) {
        int regionZ = getRegionZ(chunkZ);
        return chunkZ - (regionZ * REGION_SIZE);
    }
    
    public void closeAll() {
        for (RegionFile regionFile : openRegions.values()) {
            try {
                regionFile.close();
            } catch (Exception e) {
                logger.error("关闭区域文件失败", e);
            }
        }
        openRegions.clear();
    }
}

