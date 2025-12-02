package com.ksptool.ourcraft.sharedcore.world;
import lombok.Setter;

import java.time.LocalDateTime;

import org.joml.Vector4f;

import com.ksptool.ourcraft.sharedcore.StdRegName;

import lombok.Getter;

/**
 * 世界模板抽象类，定义世界模板的基本属性和方法
 */
@Setter
@Getter
public abstract class WorldTemplate {

    @Getter
    private StdRegName stdRegName;

    private String displayName;

    /**
     * Planned Physical Settings
     */
    private double surfaceGravityMultiplier;

    private double escapeVelocity;

    private double pressure;

    private double radius;

    private int seaLevel;

    private double maxTemperature;

    private double minTemperature;

    private double maxHumidity;

    private double minHumidity;


    /**
     * World Logical Settings
     */
    private int maxLogicHeight;

    private int minLogicHeight;

    private int maxHeight;

    private int minHeight;

    private double coordinateScale;

    /**
     * World Visual Settings
     */
    private Vector4f skyColor;

    private double fogDensity;

    private double skyLightLevel;

    private double ambientLightLevel;

    /**
     * World Engine Settings
     */
    private int actionPerSecond = 20; //该世界每秒执行Action的次数 默认20

    private int chunkMaxTTL = 6000; //以APS算
    private int chunkSizeX = 16;
    private int chunkSizeZ = 16;
    private int chunkSizeY = 256;

    //最大玩家区块租约TTL(当服务器为20APS时 大约为30分钟 (20*60)*30=6000)
    private int maxPlayerChunkLeaseAction = 6000;

    //1世界天 = 24000 Actions (约20分钟现实时间 计算公式:24000/20/60=20)
    //1世界天 = 86400世界秒(计算公式:24000*3.6=86400)
    //1Action = 3.6世界秒(计算公式:86400/24000=3.6)
    private double worldSecondsPerAction = 3.6;

    //世界开始时间(默认为0001年3月1日 通常从早晨6点开始)
    private LocalDateTime startDateTime = LocalDateTime.of(1, 3, 1, 6, 0, 0);

    private StdRegName terrainGenerator = StdRegName.of("ourcraft:terrain_generator:earth_like");
}

