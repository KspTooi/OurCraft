package com.ksptool.ourcraft.prototype;

import com.atr.jme.font.TrueTypeFont;
import com.atr.jme.font.asset.TrueTypeKeyBMP;
import com.atr.jme.font.asset.TrueTypeLoader;
import com.atr.jme.font.util.Style;
import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetManager;
import com.ksptool.ourcraft.clientjme.commons.FontSize;
import com.simsilica.lemur.*;
import com.simsilica.lemur.style.BaseStyles;
import com.simsilica.lemur.style.Styles;

import java.util.HashMap;
import java.util.Map;

public class HelloLemurWithTTF extends SimpleApplication {

    public static void main(String[] args) {
        HelloLemurWithTTF app = new HelloLemurWithTTF();
        app.start();
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

    @Override
    public void simpleInitApp() {

        assetManager.registerLoader(TrueTypeLoader.class, "ttf");
        String fontPath = "textures/font/AlibabaPuHuiTi-3-55-Regular.ttf";

        //超采样加载全部字体
        Map<FontSize, TrueTypeFont<?, ?>> fonts = new HashMap<>();
        for (FontSize fontSize : FontSize.values()) {
            fonts.put(fontSize, loadCrispFont(assetManager, fontPath, Style.Plain, fontSize.getSize()));
        }


        //初始化 Lemur (必须！) ---
        GuiGlobals.initialize(this);

        BaseStyles.loadGlassStyle();
        GuiGlobals.getInstance().getStyles().setDefaultStyle("glass");

        Styles styles = GuiGlobals.getInstance().getStyles();
        styles.getSelector(Label.ELEMENT_ID, "glass").set("font", fonts.get(FontSize.NORMAL));
        styles.getSelector(Button.ELEMENT_ID, "glass").set("font", fonts.get(FontSize.NORMAL));
        styles.getSelector(TextField.ELEMENT_ID, "glass").set("font", fonts.get(FontSize.NORMAL));


        Container myWindow = new Container();


        myWindow.addChild(new Label("测试中文"));

        Button clickMe = myWindow.addChild(new Button("22222222222"));
        Button exitBtn = myWindow.addChild(new Button("33333333333"));

        clickMe.addClickCommands(source -> {
            System.out.println("你点击了按钮！");
            source.setText("我被点击了！"); // 动态修改文字
        });

        exitBtn.addClickCommands(source -> stop());

        myWindow.setLocalTranslation(
                (float) settings.getWidth() / 2 - 100, // x
                (float) settings.getHeight() / 2 + 100, // y
                0 // z
        );

        guiNode.attachChild(myWindow);
    }

}