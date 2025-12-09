package com.ksptool.ourcraft.prototype;

import com.jme3.app.SimpleApplication;
import com.jme3.font.BitmapFont;
import com.simsilica.lemur.*;
import com.simsilica.lemur.style.BaseStyles;
import com.simsilica.lemur.style.Styles;

public class HelloLemur extends SimpleApplication {

    public static void main(String[] args) {
        HelloLemur app = new HelloLemur();
        app.start();
    }

    @Override
    public void simpleInitApp() {

        GuiGlobals.initialize(this);

        BaseStyles.loadGlassStyle();
        GuiGlobals.getInstance().getStyles().setDefaultStyle("glass");

        BitmapFont font = assetManager.loadFont("textures/font/fnt/阿里巴巴普惠.fnt");

        Styles styles = GuiGlobals.getInstance().getStyles();
        styles.getSelector(Label.ELEMENT_ID, "glass").set("font", font);
        styles.getSelector(Button.ELEMENT_ID, "glass").set("font", font);
        styles.getSelector(TextField.ELEMENT_ID, "glass").set("font", font);

        Container myWindow = new Container();

        myWindow.addChild(new Label("测试中文"));
        Button clickMe = myWindow.addChild(new Button("22222222222"));
        Button exitBtn = myWindow.addChild(new Button("33333333333"));

        clickMe.addClickCommands(new Command<Button>() {
            @Override
            public void execute(Button source) {
                System.out.println("你点击了按钮！");
                source.setText("我被点击了！"); // 动态修改文字
            }
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