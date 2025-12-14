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

import java.util.concurrent.ExecutorService;

@Slf4j
public class OurCraftClientJ extends SimpleApplication {

    //服务端世界执行单元线程池(用于运行世界逻辑ACTION)
    private ExecutorService CWEU_THREAD_POOL;

    //区块工作线程池(用于处理区块加载、生成、卸载存盘等任务)
    private ExecutorService CHUNK_PROCESS_THREAD_POOL;

    //网络线程池(用于处理网络连接、心跳、数据包接收发送等任务(虚拟线程))
    private ExecutorService NETWORK_THREAD_POOL;


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
