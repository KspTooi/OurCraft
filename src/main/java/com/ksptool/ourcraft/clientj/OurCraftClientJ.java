package com.ksptool.ourcraft.clientj;

import com.jme3.app.SimpleApplication;
import com.jme3.math.ColorRGBA;
import com.ksptool.ourcraft.clientj.service.GlobalFontService;
import com.ksptool.ourcraft.clientj.service.GuiService;
import com.ksptool.ourcraft.clientj.state.MainMenuState;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OurCraftClientJ extends SimpleApplication {


    private GuiService guiService;

    private MainMenuState mms;

    @Override
    public void simpleInitApp() {

        GlobalFontService.init(this, "textures/font/fnt/阿里巴巴普惠.fnt", "textures/font/AlibabaPuHuiTi-3-55-Regular.ttf");

        guiService = new GuiService(this);

        mms = new MainMenuState(this);
        viewPort.setBackgroundColor(ColorRGBA.White);
        inputManager.setCursorVisible(true);

        stateManager.attach(mms);
    }

    @Override
    public void reshape(int w, int h) {
        super.reshape(w, h);
        if (mms != null) {
            mms.reshape(w, h);
        }

    }
}
