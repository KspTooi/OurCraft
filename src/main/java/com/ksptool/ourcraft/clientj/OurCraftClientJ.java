package com.ksptool.ourcraft.clientj;

import com.jme3.app.SimpleApplication;
import com.jme3.math.ColorRGBA;
import com.ksptool.ourcraft.clientj.service.GlobalFontService;
import com.ksptool.ourcraft.clientj.service.GuiService;
import com.ksptool.ourcraft.clientj.service.StateService;
import com.ksptool.ourcraft.clientj.state.LoadingState;
import com.ksptool.ourcraft.clientj.state.MainMenuState;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OurCraftClientJ extends SimpleApplication {


    private GuiService guiService;

    private StateService stateService;

    private MainMenuState mms;
    private LoadingState ls;


    @Override
    public void simpleInitApp() {

        GlobalFontService.init(this, "textures/font/fnt/阿里巴巴普惠.fnt", "textures/font/AlibabaPuHuiTi-3-55-Regular.ttf");
        guiService = new GuiService(this);

        mms = new MainMenuState(this);
        ls = new LoadingState(this);
        viewPort.setBackgroundColor(ColorRGBA.White);
        inputManager.setCursorVisible(true);
        stateService = new StateService(this);

        //立即切换到主菜单状态
        stateService.toMain();
    }

    @Override
    public void reshape(int w, int h) {
        super.reshape(w, h);
        if (mms != null) {
            mms.reshape(w, h);
        }
        if (ls != null) {
            ls.reshape(w, h);
        }
    }
}
