package com.ksptool.ourcraft.server;

import com.ksptool.ourcraft.server.world.ServerWorld;
import com.ksptool.ourcraft.server.world.ServerWorldTimeService;
import com.ksptool.ourcraft.sharedcore.world.WorldTemplate;
import com.ksptool.ourcraft.sharedcore.world.WorldTemplateEarthLike;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ServerWorldTimeService测试类
 * 测试getActions方法的正确性
 */
public class TServerWorldTimeService {

    private ServerWorldTimeService timeService;
    private WorldTemplate template;

    @BeforeEach
    public void setUp() {
        template = new WorldTemplateEarthLike();
        template.setActionPerSecond(20);
        
        ServerWorld mockWorld = new MockServerWorld(template);
        timeService = new ServerWorldTimeService(mockWorld, 0L);
    }

    /**
     * 测试1秒转换为20 Actions
     */
    @Test
    public void testGetActionsOneSecond() {
        long actions = timeService.getActions(1, TimeUnit.SECONDS);
        assertEquals(20, actions, "1秒应该等于20 Actions");
    }

    /**
     * 测试2秒转换为40 Actions
     */
    @Test
    public void testGetActionsTwoSeconds() {
        long actions = timeService.getActions(2, TimeUnit.SECONDS);
        assertEquals(40, actions, "2秒应该等于40 Actions");
    }

    /**
     * 测试1分钟转换为1200 Actions (20 * 60)
     */
    @Test
    public void testGetActionsOneMinute() {
        long actions = timeService.getActions(1, TimeUnit.MINUTES);
        assertEquals(1200, actions, "1分钟应该等于1200 Actions");
    }

    /**
     * 测试1小时转换为72000 Actions (20 * 60 * 60)
     */
    @Test
    public void testGetActionsOneHour() {
        long actions = timeService.getActions(1, TimeUnit.HOURS);
        assertEquals(72000, actions, "1小时应该等于72000 Actions");
    }

    /**
     * 测试不同actionPerSecond值
     */
    @Test
    public void testGetActionsWithDifferentAPS() {
        WorldTemplate customTemplate = new WorldTemplateEarthLike();
        customTemplate.setActionPerSecond(10);
        ServerWorld mockWorld = new MockServerWorld(customTemplate);
        ServerWorldTimeService customTimeService = new ServerWorldTimeService(mockWorld, 0L);
        
        long actions = customTimeService.getActions(1, TimeUnit.SECONDS);
        assertEquals(10, actions, "当actionPerSecond=10时，1秒应该等于10 Actions");
    }

    /**
     * 测试零值
     */
    @Test
    public void testGetActionsZero() {
        long actions = timeService.getActions(0, TimeUnit.SECONDS);
        assertEquals(0, actions, "0秒应该等于0 Actions");
    }

    /**
     * Mock ServerWorld类，仅用于测试
     */
    private static class MockServerWorld extends ServerWorld {
        public MockServerWorld(WorldTemplate template) {
            super(new OurCraftServer(null), template);
        }
    }
}
