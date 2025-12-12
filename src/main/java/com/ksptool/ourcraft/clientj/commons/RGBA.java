package com.ksptool.ourcraft.clientj.commons;

import com.jme3.math.ColorRGBA;

public class RGBA {

    // 规范化因子
    private static final float INVERSE_255 = 1.0F / 255.0F;

    /**
     * 静态工厂方法：从 0-255 的整数值创建 jMonkeyEngine 的 ColorRGBA 实例。
     *
     * @param red   红色通道 (0-255)
     * @param green 绿色通道 (0-255)
     * @param blue  蓝色通道 (0-255)
     * @param alpha 透明度通道 (0-255)
     * @return 对应的 com.jme3.math.ColorRGBA 实例
     */
    public static ColorRGBA of(int red, int green, int blue, int alpha) {

        //验证输入范围 (可选但推荐)
        // 确保所有值都在 0 到 255 之间
        if (red < 0 || red > 255 || green < 0 || green > 255 || blue < 0 || blue > 255 || alpha < 0 || alpha > 255) {
            throw new IllegalArgumentException("All RGBA values must be between 0 and 255.");
        }

        //规范化 (Normalization)
        // 将整数值乘以 1/255.0F 转换为 0.0F-1.0F 的浮点数
        float rNorm = red * INVERSE_255;
        float gNorm = green * INVERSE_255;
        float bNorm = blue * INVERSE_255;
        float aNorm = alpha * INVERSE_255;

        //构造并返回 JME 的 ColorRGBA 实例
        // JME 的 ColorRGBA 构造函数接收四个 float 参数 (r, g, b, a)
        return new ColorRGBA(rNorm, gNorm, bNorm, aNorm);
    }
}