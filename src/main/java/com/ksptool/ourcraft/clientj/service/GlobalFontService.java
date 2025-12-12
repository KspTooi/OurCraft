package com.ksptool.ourcraft.clientj.service;

import com.atr.jme.font.TrueTypeFont;
import com.atr.jme.font.asset.TrueTypeKeyBMP;
import com.atr.jme.font.asset.TrueTypeLoader;
import com.atr.jme.font.shape.TrueTypeNode;
import com.atr.jme.font.util.Style;
import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.math.ColorRGBA;
import com.ksptool.ourcraft.clientj.OurCraftClientJ;
import com.ksptool.ourcraft.clientj.commons.FontSize;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class GlobalFontService {

    @Getter
    private static BitmapFont font;

    @Getter
    private static Map<FontSize, TrueTypeFont<?, ?>> ttFont = new HashMap<>();

    private static boolean isInitialized = false;

    public static void init(OurCraftClientJ client, String fontPath, String ttfFontPath) {

        if (isInitialized) {
            return;
        }

        log.info("GlobalFontService: Initializing...");
        var startTime = System.currentTimeMillis();

        font = client.getAssetManager().loadFont(fontPath);
        var endTime = System.currentTimeMillis();
        log.info("GlobalFontService: Font loaded in {}ms", endTime - startTime);

        log.info("GlobalFontService: Loading TTF fonts...");
        startTime = System.currentTimeMillis();
        
        //超采样加载4种大小字体
        client.getAssetManager().registerLoader(TrueTypeLoader.class, "ttf");

        for (FontSize fontSize : FontSize.values()) {
            ttFont.put(fontSize, loadCrispFont(client.getAssetManager(), ttfFontPath, Style.Plain, fontSize.getSize()));
        }
        endTime = System.currentTimeMillis();
        log.info("GlobalFontService: TTF fonts loaded in {}ms", endTime - startTime);

        isInitialized = true;
    }


    /**
     * 加载经过超采样优化的字体。
     *
     * @param assetManager JME的资源管理器
     * @param fontPath     TTF 文件的路径 (例如 "textures/font/MyFont.ttf")
     * @param style        字体样式 (Style.Plain, Style.Bold 等)
     * @param desiredSize  你最终想要显示在屏幕上的字号 (例如 16, 24)
     * @return 配置好的 TrueTypeFont 对象
     */
    public static TrueTypeFont<?, ?> loadCrispFont(AssetManager assetManager, String fontPath, Style style, float desiredSize) {

        int loadSize; // 实际向显存请求生成的纹理大小
        float scale;  // 最终的缩放比例

        // 基于文档的经验公式进行计算
        // 核心逻辑：字号越小，锯齿越明显，需要更大的倍率进行超采样
        if (desiredSize < 32) {
            // 小字号：除以 0.73 (大约放大 1.37 倍)
            loadSize = (int) Math.floor(desiredSize / 0.73f);
        } else if (desiredSize < 53) {
            // 中字号：除以 0.84 (大约放大 1.19 倍)
            loadSize = (int) Math.floor(desiredSize / 0.84f);
        } else {
            // 大字号：通常不需要额外放大，直接使用原大小
            loadSize = (int) desiredSize;
        }

        // 计算缩放比例：将放大的纹理缩回想要的大小
        // 例如：想要 21号，实际加载了 28号，scale = 21 / 28 = 0.75
        scale = desiredSize / (float) loadSize;

        //创建 Key，请求更大的尺寸
        TrueTypeKeyBMP key = new TrueTypeKeyBMP(fontPath, style, loadSize);

        //加载字体
        TrueTypeFont<?, ?> font = (TrueTypeFont<?, ?>) assetManager.loadAsset(key);

        //应用缩放，使其在屏幕上显示为 desiredSize
        font.setScale(scale);
        return font;
    }

    /**
     * 获取文本
     *
     * @param text     要显示的文本
     * @param fontSize 字体大小
     * @return 文本节点
     */
    public static TrueTypeNode<?> getText(String text, FontSize fontSize) {
        TrueTypeFont<?, ?> font = ttFont.get(fontSize);
        return font.getText(text, 0, ColorRGBA.Cyan);
    }

    public static TrueTypeNode<?> getText(String text, FontSize fontSize, ColorRGBA color) {
        TrueTypeFont<?, ?> font = ttFont.get(fontSize);
        return font.getText(text, 0, color);
    }


}
