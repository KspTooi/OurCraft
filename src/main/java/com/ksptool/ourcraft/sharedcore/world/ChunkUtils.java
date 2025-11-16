package com.ksptool.ourcraft.sharedcore.world;

/**
 * 区块工具类，提供客户端和服务端共享的工具方法
 */
public class ChunkUtils {
    
    /**
     * 将区块坐标转换为长整型键值
     * @param x 区块X坐标
     * @param z 区块Z坐标
     * @return 长整型键值
     */
    public static long getChunkKey(int x, int z) {
        return ((long)x << 32) | (z & 0xFFFFFFFFL);
    }
}

