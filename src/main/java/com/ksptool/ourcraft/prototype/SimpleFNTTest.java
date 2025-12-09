package com.ksptool.ourcraft.prototype;

import com.jme3.app.SimpleApplication;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.math.ColorRGBA;
import com.jme3.system.AppSettings;

public class SimpleFNTTest extends SimpleApplication {

    public static void main(String[] args) {
        SimpleFNTTest app = new SimpleFNTTest();
        AppSettings settings = new AppSettings(true);
        settings.setResolution(1280, 720);
        app.setSettings(settings);
        app.start();
    }

    @Override
    public void simpleInitApp() {


        BitmapFont myChineseFont = assetManager.loadFont("textures/font/fnt/阿里巴巴普惠.fnt");

        // 3. 创建文本对象 (相当于你的 renderText 方法)
        BitmapText hudText = new BitmapText(myChineseFont, false);

        // 4. 设置属性
        hudText.setSize(myChineseFont.getCharSet().getRenderedSize()); // 使用原字体大小
        hudText.setColor(ColorRGBA.Cyan);      // 设置颜色
        hudText.setText("Hello JME 你好世界 动态加载 TTF 测试"); // 设置文字

        // 5. 设置位置 (JME 的 GUI 坐标原点在左下角)
        // x, y, z
        hudText.setLocalTranslation(50, settings.getHeight() - 50, 0);

        //TrueTypeNode trueNode = ttf.getText("Hello World", 0, ColorRGBA.White);
        //trueNode.setLocalTranslation(0, 480, 0);
        //guiNode.attachChild(trueNode);


        // 6. 添加到 GUI 节点
        guiNode.attachChild(hudText);
    }
}