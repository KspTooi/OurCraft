package com.ksptool.ourcraft.prototype;

import java.util.HashMap;
import java.util.Map;

import com.atr.jme.font.TrueTypeBMP;
import com.atr.jme.font.TrueTypeFont;
import com.atr.jme.font.asset.TrueTypeKey;
import com.atr.jme.font.asset.TrueTypeKeyBMP;
import com.atr.jme.font.asset.TrueTypeLoader;
import com.atr.jme.font.shape.TrueTypeNode;
import com.atr.jme.font.util.StringContainer;
import com.atr.jme.font.util.Style;
import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.math.ColorRGBA;
import com.jme3.system.AppSettings;
import com.ksptool.ourcraft.clientjme.commons.FontSize;

public class SimpleTTFTest extends SimpleApplication {

    public static void main(String[] args) {
        SimpleTTFTest app = new SimpleTTFTest();
        AppSettings settings = new AppSettings(true);
        settings.setResolution(1280, 720);
        app.setSettings(settings);
        app.start();
    }

    @Override
    public void simpleInitApp() {

        assetManager.registerLoader(TrueTypeLoader.class, "ttf");

        //超采样加载4种大小字体
        String fontPath = "textures/font/AlibabaPuHuiTi-3-55-Regular.ttf";
        Map<FontSize, TrueTypeFont<?,?>> fonts = new HashMap<>();

        for (FontSize fontSize : FontSize.values()) {
            fonts.put(fontSize, loadCrispFont(assetManager, fontPath, Style.Plain, fontSize.getSize()));
        }

        //分别渲染4种大小字体
        var offsetY = 0;
        for (FontSize fontSize : FontSize.values()) {
            TrueTypeNode<?> ttn = fonts.get(fontSize).getText("Hello JME! \n你好，世界！\n动态加载 TTF 测试", 1, ColorRGBA.Cyan);
            ttn.setLocalTranslation(50, settings.getHeight() - 50 - offsetY, 0);
            offsetY += (int) ttn.getHeight();
            guiNode.attachChild(ttn);
        }

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
    public static TrueTypeFont<?,?> loadCrispFont(AssetManager assetManager, String fontPath, Style style, float desiredSize) {
        
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
        TrueTypeFont<?,?> font = (TrueTypeFont<?,?>) assetManager.loadAsset(key);

        //应用缩放，使其在屏幕上显示为 desiredSize
        font.setScale(scale);
        return font;
    }

}