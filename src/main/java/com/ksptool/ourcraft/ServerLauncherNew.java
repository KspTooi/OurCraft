package com.ksptool.ourcraft;

import com.ksptool.ourcraft.server.OurCraftServer;
import lombok.extern.slf4j.Slf4j;

/**
 * 独立服务器启动类，用于启动专用服务器（不依赖客户端）
 * 用法: java -jar our-craft.jar server <saveName> <worldName>
 */
@Slf4j
public class ServerLauncherNew {
    
    private static OurCraftServer ourCraftServer;
    
    void main() {

        // 注册关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("收到关闭信号，正在关闭服务器...");
            stopServer();
        }));

        String archiveName = "our_craft";
        String worldName = "earth_like";


        log.info("========================================");
        log.info("OurCraft 专用服务器启动中...");
        log.info("存档名称: {}", archiveName);
        log.info("世界名称: {}", worldName);
        log.info("========================================");

        // 启动GameServer
        ourCraftServer = new OurCraftServer(archiveName);
        ourCraftServer.start();
        log.info("服务器启动完成");
    }

    /**
     * 停止服务器
     */
    private static void stopServer() {

        if (ourCraftServer != null) {
            log.info("停止游戏服务器");
            ourCraftServer.shutdown();
            ourCraftServer = null;
        }


    }

    
}

