package com.ksptool.ourcraft.sharedcore.world;

import com.ksptool.ourcraft.sharedcore.StdRegName;
import org.joml.Vector4f;

/**
 * 出生平台世界模板
 * 一个只有一小块草皮平台的虚空世界
 */
public class WorldTemplateSpawnPlatform extends WorldTemplate {

    public WorldTemplateSpawnPlatform() {

        setStdRegName(StdRegName.of("ourcraft:spawn_platform"));
        setDisplayName("Spawn Platform");
        setSurfaceGravityMultiplier(1.0D);
        setEscapeVelocity(11186.0);
        setPressure(101325.0);
        setRadius(6371000.0);
        setSeaLevel(64);
        setMaxTemperature(50.0);
        setMinTemperature(-50.0);
        setMaxHumidity(100.0);
        setMinHumidity(0.0);
        setMaxLogicHeight(255);
        setMinLogicHeight(0);
        setMaxHeight(320);
        setMinHeight(-64);
        setCoordinateScale(1.0);
        setSkyColor(new Vector4f(0.1f, 0.1f, 0.2f, 1.0f));  // 深蓝色天空
        setFogDensity(0.0001);
        setSkyLightLevel(15.0);
        setAmbientLightLevel(0.0);
        setActionPerSecond(20);
        setChunkSizeX(16);
        setChunkSizeY(320);
        setChunkSizeZ(16);
        
        // 指定使用出生平台生成器
        setTerrainGenerator(StdRegName.of("ourcraft:terrain_generator:spawn_platform"));
    }
}

