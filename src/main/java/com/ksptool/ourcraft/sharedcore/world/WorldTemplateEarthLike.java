package com.ksptool.ourcraft.sharedcore.world;

import com.ksptool.ourcraft.sharedcore.StdRegName;
import org.joml.Vector4f;

/**
 * 地球类似世界模板
 * 模拟地球的物理和视觉特性，包括标准重力、大气压、温度范围等参数
 */
public class WorldTemplateEarthLike extends WorldTemplate {

    public WorldTemplateEarthLike() {


        setStdRegName(StdRegName.of("ourcraft:earth_like"));      //标准注册名称
        setDisplayName("Earth Like");                                   //显示名称
        setSurfaceGravityMultiplier(1.0D);                              //默认重力加速度乘数
        setEscapeVelocity(11186.0);                                     //逃逸速度
        setPressure(101325.0);                                          //大气压
        setRadius(6371000.0);                                           //半径
        setSeaLevel(64);                                                //海平面高度
        setMaxTemperature(50.0);                                        //最大温度
        setMinTemperature(-50.0);                                       //最小温度
        setMaxHumidity(100.0);                                          //最大湿度
        setMinHumidity(0.0);                                            //最小湿度
        setMaxLogicHeight(255);                                         //最大逻辑高度
        setMinLogicHeight(0);                                           //最小逻辑高度
        setMaxHeight(320);                                              //最大高度
        setMinHeight(-64);                                              //最小高度
        setCoordinateScale(1.0);                                        //坐标缩放比例
        setSkyColor(new Vector4f(0.529f, 0.808f, 0.922f, 1.0f));  //天空颜色
        setFogDensity(0.0001);                                          //雾密度
        setSkyLightLevel(15.0);                                         //天空光照等级
        setAmbientLightLevel(0.0);                                      //环境光照等级
        setActionPerSecond(20);                                                     //该世界的TPS
        setChunkSizeX(16);                                              //区块大小X轴
        setChunkSizeY(320);                                             //区块大小Y轴
        setChunkSizeZ(16);                                              //区块大小Z轴

        // 指定使用类地生成器
        setTerrainGenerator(StdRegName.of("ourcraft:terrain_generator:earth_like"));
    }
}
