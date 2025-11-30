package com.ksptool.ourcraft.server.archive;

import java.io.IOException;
import java.nio.file.Paths;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.ksptool.ourcraft.sharedcore.enums.EngineDefault;
import com.ksptool.ourcraft.sharedcore.utils.position.ChunkPos;
import com.ksptool.ourcraft.sharedcore.utils.position.ScaLocalPos;
import com.ksptool.ourcraft.sharedcore.utils.position.ScaPos;
import lombok.extern.slf4j.Slf4j;

/**
 * 超级区块归档管理器
 * 负责管理超级区块归档文件(SCA)的创建、打开和缓存
 * 使用了基于LRU算法的缓存，防止打开文件句柄过多导致的操作系统报错
 */
@Slf4j
public class ArchiveSuperChunkService {

    //最大打开SCAF的句柄数量(防止内存溢出)
    public static int MAX_OPEN_SCAF = 256;

    //归档管理器
    private final ArchiveService archiveService;

    //SCAF缓存
    private final Cache<String, SuperChunkArchiveFile> scafCache;

    public ArchiveSuperChunkService(ArchiveService archiveService){

        this.archiveService = archiveService;

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
     * @param scaPos SCA文件坐标
     * @return 超级区块归档文件
     */
    private SuperChunkArchiveFile openSCAF(String worldName, ScaPos scaPos){

        //生成缓存Key：使用SCA文件名作为Key，确保同一SCA文件只打开一个句柄
        String cacheKey = worldName + "." + scaPos.toScaFileName();

        //查询是否位于缓存中
        SuperChunkArchiveFile scaf = scafCache.getIfPresent(cacheKey);

        if (scaf != null) {
            return scaf;
        }

        //不在缓存中，打开新的SCAF句柄
        var dirPath = Paths.get(archiveService.getArchiveScaDirAbsolutePath(worldName));

        try {
            scaf = new SuperChunkArchiveFile(dirPath.resolve(scaPos.toScaFileName()), EngineDefault.SCA_PACKAGE_SIZE);
            scaf.open();

            log.info("打开SCAF文件: {}", dirPath.resolve(scaPos.toScaFileName()).toString());

            //缓存SCAF句柄
            scafCache.put(cacheKey, scaf);
            return scaf;
        } catch (IOException e) {
            log.error("打开SCA文件失败: {}", dirPath.resolve(scaPos.toScaFileName()).toString(), e);
            return null;
        }
    }

    /**
     * 判断区块数据是否存在于归档中
     * @param worldName 世界名称
     * @param pos 区块位置
     * @return 是否存在
     */
    public boolean hasChunk(String worldName, ChunkPos pos){
        if (worldName == null || pos == null) {
            return false;
        }

        //转换为SCA文件坐标和内部局部坐标
        ScaPos scaPos = pos.toScaPos(EngineDefault.SCA_PACKAGE_SIZE);
        ScaLocalPos scaLocalPos = pos.toScaLocalPos(EngineDefault.SCA_PACKAGE_SIZE);

        //打开或获取SCAF
        SuperChunkArchiveFile scaf = openSCAF(worldName, scaPos);
        if (scaf == null) {
            return false;
        }

        try {
            return scaf.hasChunk(scaLocalPos.getX(), scaLocalPos.getZ());
        } catch (IOException e) {
            log.error("判断区块是否存在失败: worldName={}, pos={}", worldName, pos, e);
            return false;
        }
    }

    /**
     * 读取区块数据
     * @param worldName 世界名称
     * @param pos 区块位置
     * @return 区块数据
     */
    public byte[] readChunk(String worldName, ChunkPos pos){
        if (worldName == null || pos == null) {
            return null;
        }

        //转换为SCA文件坐标和内部局部坐标
        ScaPos scaPos = pos.toScaPos(EngineDefault.SCA_PACKAGE_SIZE);
        ScaLocalPos scaLocalPos = pos.toScaLocalPos(EngineDefault.SCA_PACKAGE_SIZE);

        //打开或获取SCAF
        SuperChunkArchiveFile scaf = openSCAF(worldName, scaPos);
        if (scaf == null) {
            return null;
        }

        try {
            return scaf.readChunk(scaLocalPos.getX(), scaLocalPos.getZ());
        } catch (IOException e) {
            log.error("读取区块数据失败: worldName={}, pos={}", worldName, pos, e);
            return null;
        }
    }

    /**
     * 写入区块数据
     * @param worldName 世界名称
     * @param pos 区块位置
     * @param data 区块数据
     */
    public void writeChunk(String worldName, ChunkPos pos, byte[] data){
        if (worldName == null || pos == null || data == null || data.length == 0) {
            return;
        }

        //转换为SCA文件坐标和内部局部坐标
        ScaPos scaPos = pos.toScaPos(EngineDefault.SCA_PACKAGE_SIZE);
        ScaLocalPos scaLocalPos = pos.toScaLocalPos(EngineDefault.SCA_PACKAGE_SIZE);

        //打开或获取SCAF
        SuperChunkArchiveFile scaf = openSCAF(worldName, scaPos);
        if (scaf == null) {
            log.error("无法打开SCAF文件: worldName={}, scaPos={}", worldName, scaPos);
            return;
        }

        try {
            scaf.writeChunk(scaLocalPos.getX(), scaLocalPos.getZ(), data);
        } catch (IOException e) {
            log.error("写入区块数据失败: worldName={}, pos={}", worldName, pos, e);
        }
    }

    /**
     * 对当前已打开的SCAF文件进行碎片整理
     * 注意: 那些没有被打开的SCAF文件不会进行碎片整理
     */
    public void compact(){
        int count = 0;
        for (SuperChunkArchiveFile scaf : scafCache.asMap().values()) {
            try {
                scaf.compact();
                count++;
            } catch (IOException e) {
                log.error("碎片整理失败: {}", scaf.getFile().getName(), e);
            }
        }
        log.info("完成碎片整理，共处理 {} 个SCAF文件", count);
    }


}
