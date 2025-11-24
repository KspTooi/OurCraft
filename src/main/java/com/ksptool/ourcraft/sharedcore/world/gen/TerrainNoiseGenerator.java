package com.ksptool.ourcraft.sharedcore.world.gen;

public interface TerrainNoiseGenerator {

    /**
     获取噪声
     @param x 坐标X
     @param y 坐标Y
     @param z 坐标Z
     @return 噪声值
     */
    double getNoise(double x, double y, double z);

    /**
     * 获取种子
     * @return 种子
     */
    String getSeed();


}
