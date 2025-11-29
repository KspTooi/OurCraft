package com.ksptool.ourcraft.sharedcore.world;
import lombok.Setter;
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
    private int tps;

    private int chunkMaxTTL = 6000; //以TPS算

    private int chunkSizeX = 16;
    private int chunkSizeZ = 16;
    private int chunkSizeY = 256;

    private StdRegName terrainGenerator = StdRegName.of("ourcraft:terrain_generator:earth_like");
}

