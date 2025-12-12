package com.ksptool.ourcraft.clientjme;

import com.atr.jme.font.asset.TrueTypeLoader;
import com.jme3.app.SimpleApplication;
import com.jme3.math.ColorRGBA;
import com.ksptool.ourcraft.clientjme.gui.MainMenuState;
import com.ksptool.ourcraft.clientjme.network.JmeClientNetworkService;
import com.ksptool.ourcraft.clientjme.rendering.JmeTextureManager;
import com.ksptool.ourcraft.clientjme.states.GameplayState;
import com.ksptool.ourcraft.server.world.gen.layers.BaseDensityLayer;
import com.ksptool.ourcraft.server.world.gen.layers.FeatureLayer;
import com.ksptool.ourcraft.server.world.gen.layers.SurfaceLayer;
import com.ksptool.ourcraft.server.world.gen.layers.WaterLayer;
import com.ksptool.ourcraft.sharedcore.GlobalPalette;
import com.ksptool.ourcraft.sharedcore.Registry;
import com.ksptool.ourcraft.sharedcore.enums.BlockEnums;
import com.ksptool.ourcraft.sharedcore.enums.WorldTemplateEnums;
import com.ksptool.ourcraft.sharedcore.world.gen.DefaultTerrainGenerator;
import com.ksptool.ourcraft.sharedcore.world.gen.SpawnPlatformGenerator;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Future;

/**
 * JME游戏客户端，负责渲染、输入处理和事件消费
 */
@Slf4j
public class OurCraftClientJme extends SimpleApplication {

    @Getter
    private JmeClientNetworkService clientNetworkService;

    private MainMenuState mainMenuState;
    private GameplayState gameplayState;


    @Override
    public void simpleInitApp() {
        // 注册所有引擎原版内容
        registerAllDefaultContent();
        GlobalPalette.getInstance().bake();

        // 加载纹理图集
        JmeTextureManager.getInstance().loadAtlas();

        // 初始化网络服务
        clientNetworkService = new JmeClientNetworkService();

        // 设置背景色
        viewPort.setBackgroundColor(new ColorRGBA(0.1f, 0.1f, 0.15f, 1.0f));

        // 初始化主菜单状态
        mainMenuState = new MainMenuState();
        stateManager.attach(mainMenuState);

        assetManager.registerLoader(TrueTypeLoader.class, "ttf");


        log.info("JME客户端初始化完成，显示主菜单");
    }

    /**
     * 连接到服务器并切换到游戏状态
     */
    public void connectToServer(String host, int port) throws Exception {
        log.info("开始连接到服务器: {}:{}", host, port);

        Future<com.ksptool.ourcraft.clientjme.network.JmeClientNetworkSession> future = clientNetworkService.connect(host, port);
        if (future == null) {
            log.error("连接失败：无法创建连接任务");
            return;
        }

        // 异步等待连接完成
        new Thread(() -> {
            try {
                com.ksptool.ourcraft.clientjme.network.JmeClientNetworkSession session = future.get();
                if (session != null) {
                    log.info("连接成功，切换到游戏状态");
                    // 在主线程中切换状态
                    enqueue(() -> {
                        stateManager.detach(mainMenuState);
                        gameplayState = new GameplayState();
                        stateManager.attach(gameplayState);
                    });
                } else {
                    log.error("连接失败：服务器拒绝连接");
                }
            } catch (Exception e) {
                log.error("连接过程中发生错误", e);
            }
        }).start();
    }

    /**
     * 注册所有引擎原版的内容
     */
    private void registerAllDefaultContent() {
        var registry = Registry.getInstance();

        BlockEnums.registerBlocks(registry);
        WorldTemplateEnums.registerWorldTemplate(registry);

        //注册地形生成器
        var gen = new DefaultTerrainGenerator();
        gen.addLayer(new BaseDensityLayer());
        gen.addLayer(new WaterLayer());
        gen.addLayer(new SurfaceLayer());
        gen.addLayer(new FeatureLayer());
        registry.registerTerrainGenerator(gen);

        //注册出生平台生成器
        var spawnPlatformGen = new SpawnPlatformGenerator();
        registry.registerTerrainGenerator(spawnPlatformGen);
    }
}
