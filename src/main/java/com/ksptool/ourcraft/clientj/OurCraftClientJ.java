package com.ksptool.ourcraft.clientj;

import com.jme3.app.SimpleApplication;
import com.jme3.math.ColorRGBA;
import com.ksptool.ourcraft.clientj.service.GlobalFontService;
import com.ksptool.ourcraft.clientj.service.GuiService;
import com.ksptool.ourcraft.clientj.service.ClientEventService;
import com.ksptool.ourcraft.clientj.service.ClientNetworkService;
import com.ksptool.ourcraft.clientj.service.ClientStateService;
import com.ksptool.ourcraft.clientj.world.ClientWorld;
import com.ksptool.ourcraft.sharedcore.GlobalPalette;
import com.ksptool.ourcraft.sharedcore.Registry;
import com.ksptool.ourcraft.sharedcore.enums.BlockEnums;
import com.ksptool.ourcraft.sharedcore.enums.EngineDefault;
import com.ksptool.ourcraft.sharedcore.enums.WorldTemplateEnums;
import com.ksptool.ourcraft.sharedcore.utils.ThreadFactoryUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;

@Slf4j
public class OurCraftClientJ extends SimpleApplication {

    //服务端世界执行单元线程池(用于运行世界逻辑ACTION)
    private ExecutorService CWEU_THREAD_POOL;

    //区块工作线程池(用于处理区块MESH生成)
    private ExecutorService CHUNK_PROCESS_THREAD_POOL;

    private GuiService guiService;

    @Getter
    private ClientStateService clientStateService;

    @Getter
    private ClientNetworkService clientNetworkService;

    @Getter
    private ClientEventService ces;

    @Getter
    private ClientWorld world;


    @Override
    public void simpleInitApp() {

        GlobalFontService.init(this, "textures/font/fnt/阿里巴巴普惠.fnt", "textures/font/AlibabaPuHuiTi-3-55-Regular.ttf");

        //创建事件服务
        ces = new ClientEventService();

        clientStateService = new ClientStateService(this);
        guiService = new GuiService(this);
        viewPort.setBackgroundColor(ColorRGBA.White);
        inputManager.setCursorVisible(true);

        //创建网络服务
        clientNetworkService = new ClientNetworkService(this);


        //初始化线程池
        initThreadPools();

        //立即切换到主菜单状态
        clientStateService.toMain();

        //注册全部原版内容(客户端)
        var registry = Registry.getInstance();
        BlockEnums.registerBlocks(registry);
        WorldTemplateEnums.registerWorldTemplate(registry);

        //初始化全局调色板
        GlobalPalette.getInstance().bake();

        world = new ClientWorld();
    }

    /**
     * 主窗口大小改变
     * @param w 窗口宽度
     * @param h 窗口高度
     */
    @Override
    public void reshape(int w, int h) {
        super.reshape(w, h);
        if(clientStateService != null){
            clientStateService.reshape(w, h);
        }
    }


    /**
     * 初始化所有线程池
     */
    private void initThreadPools() {

        //初始化SWEU线程池（用于运行世界逻辑）
        CWEU_THREAD_POOL = new ThreadPoolExecutor(
                0,                             // 核心线程数
                EngineDefault.getMaxSWEUThreadCount() ,                          // 最大线程数
                60L, TimeUnit.SECONDS,        // 闲置线程回收时间
                new LinkedBlockingQueue<>(EngineDefault.getMaxSWEUQueueSize()),    //最多排队任务
                ThreadFactoryUtils.createSWEUThreadFactory(),
                new ThreadPoolExecutor.DiscardPolicy()   // 拒绝策略: 队列满丢弃任务
        );

        log.info("CWEU线程池已初始化 当前:{} 最大:{} 队列大小:{}", 0,EngineDefault.getMaxSWEUThreadCount(),EngineDefault.getMaxSWEUQueueSize());

        //初始化区块处理线程池（用于区块加载、生成、卸载）
        CHUNK_PROCESS_THREAD_POOL = new ThreadPoolExecutor(
                0,                             // 核心线程数
                EngineDefault.getMaxChunkProcessThreadCount(),                          // 最大线程数
                60L, TimeUnit.SECONDS,        // 闲置线程回收时间
                new LinkedBlockingQueue<>(EngineDefault.getMaxChunkProcessQueueSize()),    //最多排队任务
                ThreadFactoryUtils.createChunkProcessThreadFactory(),
                new ThreadPoolExecutor.DiscardPolicy()   // 拒绝策略: 队列满丢弃任务
        );

        log.info("区块处理线程池已初始化 当前:{} 最大:{} 队列大小:{}", 0,EngineDefault.getMaxChunkProcessThreadCount(),EngineDefault.getMaxChunkProcessQueueSize());
    }


    /**
     * 关闭所有线程池
     */
    private void shutdownThreadPools() {
        if (CWEU_THREAD_POOL != null) {
            CWEU_THREAD_POOL.shutdown();
            log.info("CWEU线程池已关闭");
        }
        if (CHUNK_PROCESS_THREAD_POOL != null) {
            CHUNK_PROCESS_THREAD_POOL.shutdown();
            log.info("区块处理线程池已关闭");
        }
    }
}
