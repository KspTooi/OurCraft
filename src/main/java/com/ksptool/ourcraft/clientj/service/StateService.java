package com.ksptool.ourcraft.clientj.service;

import com.jme3.app.state.AppStateManager;
import com.ksptool.ourcraft.clientj.OurCraftClientJ;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * 状态服务
 * 负责管理项目中的各个状态
 */
@Slf4j
public class StateService {

    private final OurCraftClientJ client;
    private final AppStateManager stateManager;

    public StateService(OurCraftClientJ client) {
        this.client = client;
        this.stateManager = client.getStateManager();
        log.info("状态服务初始化完成");
    }

    /**
     * 切换到主菜单状态
     */
    public void toMain(){
        stateManager.attach(mms);
    }

    /**
     * 加入服务器
     * @param host 服务器主机名
     * @param port 服务器端口
     */
    public void joinServer(){

    }


    /**
     * 加入世界
     */
    public void joinWorld(){

    }

}
