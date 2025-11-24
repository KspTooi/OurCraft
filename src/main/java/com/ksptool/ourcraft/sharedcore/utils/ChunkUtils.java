package com.ksptool.ourcraft.sharedcore.utils;

import com.ksptool.ourcraft.server.archive.SuperChunkArchiveFile;

/**
 * 区块工具类，提供客户端和服务端共享的工具方法
 */
public class ChunkUtils {

    public static int CHUNK_SIZE = SuperChunkArchiveFile.SCAF_CHUNK_SIZE;
    
    /**
     * 将区块坐标转换为长整型键值
     * @param x 区块X坐标
     * @param z 区块Z坐标
     * @return 长整型键值
     */
    public static long getChunkKey(int x, int z) {
        return ((long)x << 32) | (z & 0xFFFFFFFFL);
    }

    /**
     * 获取区块缓存键值
     * @param worldName 世界名称
     * @param chunkX 区块X坐标
     * @param chunkZ 区块Z坐标
     * @return 区块缓存键值
     */
    public static String getChunkCacheKey(String worldName, int chunkX, int chunkZ) {
        return worldName + "." + getScaFileName(chunkX,chunkZ,".sca");
    }

    /**
     * 获取SCA文件名
     * @param chunkX 区块X坐标
     * @param chunkZ 区块Z坐标
     * @param suffix 文件后缀
     * @return SCA文件名 如S.1.1.sca
     */
    public static String getScaFileName(int chunkX, int chunkZ,String suffix) {
        return "S."+getRegionX(chunkX) + "." + getRegionZ(chunkZ) + "." + suffix;
    }
    
    /**
     * 获取区域X坐标
     * @param chunkX 区块X坐标
     * @return 区域X坐标
     */
    public static int getRegionX(int chunkX) {
        return chunkX >= 0 ? chunkX / CHUNK_SIZE : (chunkX + 1) / CHUNK_SIZE - 1;
    }

    /**
     * 获取区域Z坐标
     * @param chunkZ 区块Z坐标
     * @return 区域Z坐标
     */
    public static int getRegionZ(int chunkZ) {
        return chunkZ >= 0 ? chunkZ / CHUNK_SIZE : (chunkZ + 1) / CHUNK_SIZE - 1;
    }

    /**
     * 获取本地区块X坐标
     * @param chunkX 区块X坐标
     * @return 本地区块X坐标
     */
    public static int getLocalChunkX(int chunkX) {
        int regionX = getRegionX(chunkX);
        return chunkX - (regionX * CHUNK_SIZE);
    }

    /**
     * 获取本地区块Z坐标
     * @param chunkZ 区块Z坐标
     * @return 本地区块Z坐标
     */
    public static int getLocalChunkZ(int chunkZ) {
        int regionZ = getRegionZ(chunkZ);
        return chunkZ - (regionZ * CHUNK_SIZE);
    }


}

