package com.ksptool.ourcraft.server.archive;

import java.io.IOException;
import java.nio.file.Paths;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.ksptool.ourcraft.sharedcore.utils.ChunkUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * 超级区块归档管理器
 * 负责管理超级区块归档文件(SCA)的创建、打开和缓存
 * 使用了基于LRU算法的缓存，防止打开文件句柄过多导致的操作系统报错
 */
@Slf4j
public class ArchiveSuperChunkManager {

    //最大打开SCAF的句柄数量(防止内存溢出)
    public static int MAX_OPEN_SCAF = 256;

    //归档管理器
    private final ArchiveManager archiveManager;

    //SCAF缓存
    private final Cache<String, SuperChunkArchiveFile> scafCache;

    public ArchiveSuperChunkManager(ArchiveManager archiveManager){

        this.archiveManager = archiveManager;

        /*
         * 初始化SCAF缓存
         * SCAF需要缓存才能工作，这是因为每次打开SCA文件都涉及到"系统调用开销"和"磁盘 I/O 抖动" 当SCAF中的RandomAccessFile保持打开状态时，操作系统和硬盘驱动会有缓存优化，如果频繁打开和关闭SCAF这些优化就失效了。
         */
        scafCache = Caffeine.newBuilder()
                .maximumSize(MAX_OPEN_SCAF) // 限制最大打开文件数
                .removalListener((String key, SuperChunkArchiveFile value, RemovalCause cause) -> {
                    // 当条目被移除时（无论是因大小限制、手动移除还是垃圾回收），执行关闭操作
                    try {
                        log.debug("关闭区域文件: {} (原因: {})", key, cause);
                        value.close();
                    } catch (Exception e) {
                        log.error("关闭区域文件失败: {}", key, e);
                    }
                })
                .build();
    }

    /**
     * 打开超级区块归档文件(SCAF)
     * @param worldName 世界名称
     * @param chunkX 全局区块X坐标
     * @param chunkZ 全局区块Z坐标
     * @return 超级区块归档文件
     */
    public SuperChunkArchiveFile openSCAF(String worldName, int chunkX, int chunkZ){

        //获取区块缓存键
        String key = ChunkUtils.getChunkCacheKey(worldName, chunkX, chunkZ);

        //查询是否位于缓存中
        SuperChunkArchiveFile scaf = scafCache.getIfPresent(key);

        if (scaf != null) {
            return scaf;
        }

        //不在缓存中，打开新的SCAF句柄
        var dirPath = Paths.get(archiveManager.getArchiveScaDirAbsolutePath(worldName));

        try {
            scaf = new SuperChunkArchiveFile(dirPath.resolve(ChunkUtils.getScaFileName(chunkX, chunkZ, "sca")));
            scaf.open();

            log.info("打开SCAF文件: {}", dirPath.resolve(ChunkUtils.getScaFileName(chunkX, chunkZ, "sca")).toString());

            //缓存SCAF句柄
            scafCache.put(key, scaf);
            return scaf;
        } catch (IOException e) {
            log.error("打开SCA文件失败: {}", dirPath.resolve(ChunkUtils.getScaFileName(chunkX, chunkZ, "sca")).toString(), e);
            return null;
        }
    }

    


}
