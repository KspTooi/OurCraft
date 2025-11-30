package com.ksptool.ourcraft.server.world;

import com.ksptool.ourcraft.server.OurCraftServer;
import com.ksptool.ourcraft.server.archive.ArchiveService;
import com.ksptool.ourcraft.server.archive.ArchivePaletteService;
import com.ksptool.ourcraft.server.archive.ArchiveWorldService;
import com.ksptool.ourcraft.server.archive.model.ArchiveWorldIndexDto;
import com.ksptool.ourcraft.server.archive.model.ArchiveWorldIndexVo;
import org.apache.commons.lang3.StringUtils;
import com.ksptool.ourcraft.server.world.save.RegionManager;
import com.ksptool.ourcraft.sharedcore.GlobalPalette;
import com.ksptool.ourcraft.sharedcore.Registry;
import com.ksptool.ourcraft.sharedcore.world.WorldTemplate;
import lombok.extern.slf4j.Slf4j;
import java.io.File;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端世界管理器
 * 负责管理所有服务端世界
 */
@Slf4j
public class ServerWorldService {

    // 世界执行单元 世界名称 -> 世界执行单元
    private final Map<String, ServerWorldExecutionUnit> worldExecutors = new ConcurrentHashMap<>();

    // 暂时直接使用OurCraftServerInstance的引用来获取网络连接(因为网络层还未完成，目前它是全局的)
    private final OurCraftServer server;

    public ServerWorldService(OurCraftServer server) {
        this.server = server;
    }

    /**
     * 创建世界，当世界存在时不进行任何操作
     *
     * @param worldName          世界名称
     * @param worldTemplateRegId 世界模板注册ID
     */
    public boolean createWorld(String worldName, String worldTemplateRegId) {
        return createWorld(worldName, null, worldTemplateRegId);
    }

    /**
     * 创建世界，当世界存在时不进行任何操作
     * @param worldName 世界名称
     * @param seed 世界种子
     * @param worldTemplateRegId 世界模板注册ID
     */
    public boolean createWorld(String worldName,String seed, String worldTemplateRegId) {

        if (StringUtils.isBlank(worldName)) {
            log.error("创建世界失败: 世界名称不能为空");
            return false;
        }

        if (StringUtils.isBlank(worldTemplateRegId)) {
            log.error("创建世界失败: 世界模板ID不能为空");
            return false;
        }

        if (worldExecutors.containsKey(worldName)) {
            log.warn("创建世界失败: 世界 {} 已经在运行中", worldName);
            return false;
        }

        ArchiveService archiveService = server.getArchiveService();
        if (archiveService == null) {
            log.error("创建世界失败: 归档管理器不存在");
            return false;
        }

        ArchiveWorldService archiveWorldService = archiveService.getWorldService();
        if (archiveWorldService == null) {
            log.error("创建世界失败: 归档世界管理器不存在");
            return false;
        }

        ArchiveWorldIndexVo existWorldIndex = archiveWorldService.loadWorldIndex(worldName);
        if (existWorldIndex != null) {
            //log.warn("创建世界失败: 世界 {} 已存在于归档中", worldName);
            return false;
        }

        WorldTemplate template = Registry.getInstance().getWorldTemplate(worldTemplateRegId);
        if (template == null) {
            log.error("创建世界失败: 世界模板 {} 未注册", worldTemplateRegId);
            return false;
        }

        //如果种子为空，则使用随机种子
        if (StringUtils.isBlank(seed)) {
            seed = String.valueOf(new java.util.Random().nextLong());
        }

        //创建世界索引数据
        ArchiveWorldIndexDto dto = new ArchiveWorldIndexDto();
        dto.setName(worldName);
        dto.setSeed(seed);
        dto.setTotalTick(0L);
        dto.setTemplateStdRegName(worldTemplateRegId);
        dto.setSpawnX(0);
        dto.setSpawnY(64);
        dto.setSpawnZ(0);
        dto.setDefaultSpawnCreated(0);

        archiveWorldService.saveWorldIndex(dto);
        log.info("世界 {} 创建成功 (模板: {})", worldName, worldTemplateRegId);
        return true;
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
        
        //获取归档管理器
        ArchiveService archiveService = server.getArchiveService();
        ArchiveWorldService archiveWorldService = archiveService.getWorldService();
        ArchivePaletteService archivePaletteService = archiveService.getPaletteService();

        //加载归档中保存的调色板
        var count = archivePaletteService.loadGlobalPalette(GlobalPalette.getInstance());

        if(count == 0){
            GlobalPalette.getInstance().reBake();
            log.warn("归档 {} 中未找到调色板,使用默认调色板", archiveService.getCurrentArchiveName());
        }

        //读取世界索引
        ArchiveWorldIndexVo worldIndexVo = archiveWorldService.loadWorldIndex(worldName);

        if(worldIndexVo == null){
            log.error("无法加载世界 {} 因为世界索引不存在.世界可能尚未创建或已删除.", worldName);
            return;
        }

        //在注册表查找要加载的世界使用的世界模板
        WorldTemplate template = Registry.getInstance().getWorldTemplate(worldIndexVo.getTemplateStdRegName());

        if(template == null){
            log.error("无法加载世界 {} 因为世界模板未注册.世界模板={}", worldName, worldIndexVo.getTemplateStdRegName());
            return;
        }

        ServerWorld sw = new ServerWorld(server,template);
        sw.setName(worldIndexVo.getName());
        sw.setSeed(worldIndexVo.getSeed());
        sw.setGameTime(worldIndexVo.getTotalTick());
        sw.setSaveName(worldIndexVo.getName());
        sw.setArchiveService(archiveService);

        RegionManager eam = new RegionManager(new File("chunksDir"), ".sce", "SCEF");

        //sw.setRegionManager(aam);
        sw.setEntityRegionManager(eam);

        sw.init();

        ServerWorldExecutionUnit sweu = new ServerWorldExecutionUnit(sw,server);
        worldExecutors.put(worldName,sweu);
        log.info("世界 {} 已准备就绪", worldName);
    }



    /**
     * 保存世界
     * 
     * @param worldName 世界名称
     */
    public void saveWorld(String worldName) {

        ServerWorldExecutionUnit unit = worldExecutors.get(worldName);
        if (unit == null) {
            log.error("保存世界失败: 世界 {} 不存在或未加载", worldName);
            return;
        }

        ServerWorld world = unit.getServerWorld();
        if (world == null) {
            log.error("保存世界失败: 世界实例为空 {}", worldName);
            return;
        }

        ArchiveService archiveService = server.getArchiveService();
        if (archiveService == null) {
            log.error("保存世界失败: 归档管理器不存在 {}", worldName);
            return;
        }

        ArchiveWorldService archiveWorldService = archiveService.getWorldService();
        if (archiveWorldService == null) {
            log.error("保存世界失败: 归档世界管理器不存在 {}", worldName);
            return;
        }

        //保存世界
        archiveWorldService.saveWorld(world);
    }

    /**
     * 卸载世界并保存
     * 
     * @param worldName 世界名称
     */
    public void unloadWorldAndSave(String worldName) {
        log.info("正在卸载并保存世界 {}", worldName);
        saveWorld(worldName);
        stopWorld(worldName);
        worldExecutors.remove(worldName);
        log.info("世界 {} 已卸载并保存", worldName);
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
        server.getSWEU_THREAD_POOL().submit(worldExecutor);
        log.info("世界 {} 已提交到SWEU", worldName);
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
            saveWorld(worldName);
        }
        worldExecutors.clear();
        server.getSWEU_THREAD_POOL().shutdown();
        log.info("世界管理器已离线，所有世界已保存到归档");
    }

}
