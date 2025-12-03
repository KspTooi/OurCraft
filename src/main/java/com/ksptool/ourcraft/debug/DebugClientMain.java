package com.ksptool.ourcraft.debug;

import com.ksptool.ourcraft.server.world.gen.layers.BaseDensityLayer;
import com.ksptool.ourcraft.server.world.gen.layers.FeatureLayer;
import com.ksptool.ourcraft.server.world.gen.layers.SurfaceLayer;
import com.ksptool.ourcraft.server.world.gen.layers.WaterLayer;
import com.ksptool.ourcraft.sharedcore.GlobalPalette;
import com.ksptool.ourcraft.sharedcore.Registry;
import com.ksptool.ourcraft.sharedcore.enums.BlockEnums;
import com.ksptool.ourcraft.sharedcore.world.gen.DefaultTerrainGenerator;
import lombok.extern.slf4j.Slf4j;

/**
 * 调试客户端主入口
 */
@Slf4j
public class DebugClientMain {
    
    public static void main(String[] args) {
        log.info("启动调试客户端...");
        
        registerAllDefaultContent();
        
        GlobalPalette.getInstance().bake();
        
        DebugClient client = new DebugClient();
        client.init();
        client.start();
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("关闭调试客户端...");
            client.stop();
        }));
    }
    
    private static void registerAllDefaultContent() {
        Registry registry = Registry.getInstance();
        
        BlockEnums.registerBlocks(registry);
        
        var gen = new DefaultTerrainGenerator();
        gen.addLayer(new BaseDensityLayer());
        gen.addLayer(new WaterLayer());
        gen.addLayer(new SurfaceLayer());
        gen.addLayer(new FeatureLayer());
        registry.registerTerrainGenerator(gen);
    }
}

