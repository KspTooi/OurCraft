package com.ksptool.ourcraft.server.world;

import com.ksptool.ourcraft.server.OurCraftServer;
import com.ksptool.ourcraft.sharedcore.events.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 服务端世界执行单元
 * 负责执行单个服务端世界的逻辑（Tick循环、物理更新、事件处理、网络同步）
 */
@Slf4j
public class ServerWorldExecutionUnit implements Runnable {

    private final String worldName;

    private final ServerWorld serverWorld;

    @Getter
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    private final int actionPerSecond;

    public ServerWorldExecutionUnit(ServerWorld serverWorld, OurCraftServer serverInstance) {
        this.worldName = serverWorld.getName();
        this.serverWorld = serverWorld;
        this.actionPerSecond = serverWorld.getTemplate().getActionPerSecond();
    }

    @Override
    public void run() {

        log.info("世界 {} 已启动 APS:{}", worldName, actionPerSecond);
        isRunning.set(true);

        if (serverWorld.getTemplate() == null) {
            log.error("无法启动世界 {}: template 为 null", worldName);
            isRunning.set(false);
            return;
        }

        final double tickRate = serverWorld.getTemplate().getActionPerSecond();
        final double tickTime = 1.0 / tickRate;

        double lastTime = System.nanoTime() / 1_000_000_000.0;
        double accumulator = 0.0;

        while (isRunning.get()) {
            try {
                double now = System.nanoTime() / 1_000_000_000.0;
                double deltaSeconds = now - lastTime;
                lastTime = now;

                accumulator += deltaSeconds;

                // 追赶逻辑 (Catch-up)
                while (accumulator >= tickTime) {
                    tick((float) tickTime);
                    accumulator -= tickTime;
                }
            } catch (Throwable t) {
                log.error("世界循环发生严重错误", t);
                System.exit(-1);
            }

            // 防止 CPU 空转
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                isRunning.set(false);
            }
        }

        log.info("世界执行单元已停止: {}", worldName);
    }

    public void stop() {
        isRunning.set(false);
    }

    /**
     * 执行一次逻辑 Tick
     */
    private void tick(float tickDelta) {
        serverWorld.action(tickDelta);
    }

    /**
     * 获取服务器世界
     * 
     * @return 服务器世界
     */
    public ServerWorld getServerWorld() {
        return serverWorld;
    }
}