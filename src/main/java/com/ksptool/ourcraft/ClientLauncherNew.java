package com.ksptool.ourcraft;

import com.jme3.system.AppSettings;
import com.ksptool.ourcraft.clientj.OurCraftClientJ;
import com.ksptool.ourcraft.sharedcore.enums.EngineDefault;

public class ClientLauncherNew {

    public static void main(String[] args) {
        OurCraftClientJ app = new OurCraftClientJ();

        AppSettings settings = new AppSettings(true);
        settings.setResolution(1280, 720);
        settings.setMinResolution(800, 600);
        settings.setMinHeight(800);
        settings.setMinWidth(600);
        settings.setTitle("ClientJ" + EngineDefault.ENGINE_VERSION + "开发预览");
        settings.setResizable(true);
        settings.setVSync(true);

        app.setSettings(settings);
        app.start();
    }

}