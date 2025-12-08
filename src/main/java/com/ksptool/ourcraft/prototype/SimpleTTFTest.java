package com.ksptool.ourcraft.prototype;

import com.jme3.app.SimpleApplication;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.math.ColorRGBA;
import com.jme3.system.AppSettings;

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


        BitmapFont myChineseFont = assetManager.loadFont("textures/font/fnt/微软雅黑.fnt");


        // 3. 创建文本对象 (相当于你的 renderText 方法)
        BitmapText hudText = new BitmapText(myChineseFont, false);
        
        // 4. 设置属性
        hudText.setSize(myChineseFont.getCharSet().getRenderedSize()); // 使用原字体大小
        hudText.setColor(ColorRGBA.Cyan);      // 设置颜色
        hudText.setText("Hello JME! \n你好，世界！\n动态加载 TTF 测试"); // 设置文字
        
        // 5. 设置位置 (JME 的 GUI 坐标原点在左下角)
        // x, y, z
        hudText.setLocalTranslation(50, settings.getHeight() - 50, 0); 

        // 6. 添加到 GUI 节点
        guiNode.attachChild(hudText);
    }
}