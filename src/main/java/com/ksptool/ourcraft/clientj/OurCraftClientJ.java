package com.ksptool.ourcraft.clientj;

import com.jme3.app.SimpleApplication;
import com.jme3.math.ColorRGBA;
import com.ksptool.ourcraft.clientj.service.GlobalFontService;
import com.ksptool.ourcraft.clientj.service.GuiService;
import com.ksptool.ourcraft.clientj.service.StateService;
import com.ksptool.ourcraft.clientj.state.LoadingState;
import com.ksptool.ourcraft.clientj.state.MainMenuState;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OurCraftClientJ extends SimpleApplication {

    private GuiService guiService;

    @Getter
    private StateService stateService;


    @Override
    public void simpleInitApp() {

        GlobalFontService.init(this, "textures/font/fnt/阿里巴巴普惠.fnt", "textures/font/AlibabaPuHuiTi-3-55-Regular.ttf");
        stateService = new StateService(this);
        guiService = new GuiService(this);
        viewPort.setBackgroundColor(ColorRGBA.White);
        inputManager.setCursorVisible(true);

        //立即切换到主菜单状态
        stateService.toMain();
    }

    @Override
    public void reshape(int w, int h) {
        super.reshape(w, h);
        if(stateService != null){
            stateService.reshape(w, h);
        }

    }
}
