package com.ksptool.ourcraft.server.manager;

import com.ksptool.ourcraft.server.OurCraftServerInstance;
import com.ksptool.ourcraft.server.world.ServerWorld;
import com.ksptool.ourcraft.server.world.save.RegionManager;
import com.ksptool.ourcraft.server.world.save.SaveManager;
import com.ksptool.ourcraft.server.world.save.WorldIndex;
import com.ksptool.ourcraft.server.world.save.WorldMetadata;
import com.ksptool.ourcraft.sharedcore.world.GlobalPalette;
import com.ksptool.ourcraft.sharedcore.world.Registry;
import com.ksptool.ourcraft.sharedcore.world.WorldTemplateOld;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class ServerWorldManager {

    // 最大执行线程数
    @Getter
    @Setter
    private int maxExecutionThreads = Runtime.getRuntime().availableProcessors() - 1;

    // 线程索引
    private final AtomicInteger threadIndex = new AtomicInteger(0);

    private String saveName;

    // 世界执行单元 世界名称 -> 世界执行单元
    private final Map<String, ServerWorldExecutionUnit> worldExecutors = new ConcurrentHashMap<>();

    // 暂时直接使用OurCraftServerInstance的引用来获取网络连接(因为网络层还未完成，目前它是全局的)
    private final OurCraftServerInstance serverInstance;

    // 用于执行世界逻辑的线程池
    private final ExecutorService executorService;

    public ServerWorldManager(OurCraftServerInstance serverInstance, String saveName) {

        if (StringUtils.isBlank(saveName)) {
            log.error("归档名称不能为空");
            throw new IllegalArgumentException("归档名称不能为空");
        }

        this.saveName = saveName;
        this.serverInstance = serverInstance;

        // 创建线程池
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r);
            thread.setName("WorldDriverThread-" + threadIndex.getAndIncrement());
            log.info("新世界驱动线程-{} 已创建", thread.getName());
            return thread;
        });

    }

    /**
     * 将世界加载到执行单元中
     * 
     * @param worldName 世界名称
     */
    public void loadWorld(String worldName) {
        if (worldExecutors.containsKey(worldName)) {
            log.warn("世界 {} 已经加载", worldName);
            return;
        }

        log.info("正在从归档 {} 加载世界 {}", saveName, worldName);

        SaveManager saveManager = SaveManager.getInstance();

        if (!saveManager.saveExists(saveName)) {
            log.error("加载世界失败: 归档 {} 不存在", saveName);
            return;
        }

        WorldIndex index = saveManager.loadWorldIndex(saveName);
        if (index == null || index.worlds == null) {
            log.error("加载世界失败: 无法读取世界索引 归档={}", saveName);
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
            log.error("加载世界失败: 世界不存在 归档={}, 世界={}", saveName, worldName);
            return;
        }

        // 确保调色板已加载
        GlobalPalette palette = GlobalPalette.getInstance();
        if (!palette.isBaked()) {
            if (!saveManager.loadPalette(saveName, palette)) {
                log.debug("调色板文件不存在，使用默认调色板 归档={}", saveName);
                palette.bake();
            } else {
                log.debug("已加载调色板");
            }
        }

        WorldTemplateOld template = Registry.getWorldTemplateOld(metadata.templateId);
        if (template == null) {
            log.warn("找不到世界模板 '{}', 使用默认模板 归档={}", metadata.templateId, saveName);
            template = com.ksptool.ourcraft.sharedcore.world.Registry.getDefaultTemplate();
            if (template == null) {
                log.error("无法加载世界: 默认模板未找到 归档={}", saveName);
                return;
            }
        }

        ServerWorld serverWorld = new ServerWorld(template);
        serverWorld.setWorldName(worldName);
        serverWorld.setSeed(metadata.seed);
        serverWorld.setGameTime(metadata.worldTime);
        serverWorld.setSaveName(saveName);

        File chunksDir = saveManager.getWorldChunkDir(saveName, worldName);
        if (chunksDir != null) {
            com.ksptool.ourcraft.server.world.save.RegionManager regionManager = new RegionManager(
                    chunksDir, ".sca", "SCAF");
            serverWorld.setRegionManager(regionManager);
        }

        File entityDir = saveManager.getWorldEntityDir(saveName, worldName);
        if (entityDir != null) {
            RegionManager entityRegionManager = new RegionManager(entityDir, ".sce", "SCEF");
            serverWorld.setEntityRegionManager(entityRegionManager);
        }

        serverWorld.init();

        // 创建执行单元
        ServerWorldExecutionUnit executionUnit = new ServerWorldExecutionUnit(serverWorld, serverInstance);
        worldExecutors.put(worldName, executionUnit);

        log.info("世界 {} 加载完成，准备就绪", worldName);
    }

    /**
     * 保存世界
     * 
     * @param worldName 世界名称
     */
    public void saveWorld(String worldName) {

    }

    /**
     * 卸载世界并保存
     * 
     * @param worldName 世界名称
     */
    public void unloadWorldAndSave(String worldName) {
        log.info("正在卸载并保存世界 {} ", worldName);
    }

    /**
     * 创建世界
     * 
     * @param worldName          世界名称
     * @param worldTemplateRegId 世界模板注册ID
     */
    public void createWorld(String worldName, String worldTemplateRegId) {

    }

    /**
     * 运行世界
     * 
     * @param worldName 世界名称
     */
    public void runWorld(String worldName) {

        // 查找世界执行单元
        ServerWorldExecutionUnit worldExecutor = worldExecutors.get(worldName);

        if (worldExecutor == null) {
            log.error("世界执行单元不存在: {}，当前世界可能尚未加载或已卸载", worldName);
            return;
        }

        // 提交到线程池执行
        executorService.submit(worldExecutor);
        log.info("世界-{} 已提交到WorldDriver", worldName);
    }

    public ServerWorld getWorld(String worldName) {
        ServerWorldExecutionUnit worldExecutor = worldExecutors.get(worldName);
        if (worldExecutor == null) {
            log.error("世界执行单元不存在: {}，当前世界可能尚未加载或已卸载", worldName);
            return null;
        }
        return worldExecutor.getServerWorld();
    }

    /**
     * 终止世界
     * 
     * @param worldName 世界名称
     */
    public void stopWorld(String worldName) {
        ServerWorldExecutionUnit worldExecutor = worldExecutors.get(worldName);
        if (worldExecutor == null) {
            log.error("世界执行单元不存在: {}，当前世界可能尚未加载或已卸载", worldName);
            return;
        }
        worldExecutor.stop();
        log.info("世界-{} 已请求WorldDriver停止", worldName);
    }

    /**
     * 停止全部世界、关闭WorldDriver并保存世界
     */
    public void shutdown() {
        for (String worldName : worldExecutors.keySet()) {
            stopWorld(worldName);
        }
        worldExecutors.clear();
        executorService.shutdown();
        log.info("世界管理器已离线，所有世界将被保存到归档: {}", saveName);

        // TODO:保存世界
    }

}
