package com.ksptool.ourcraft.clientj.service;

import com.jme3.app.state.AppStateManager;
import com.ksptool.ourcraft.clientj.OurCraftClientJ;
import com.ksptool.ourcraft.clientj.state.ArrayTextureVisualizerState;
import com.ksptool.ourcraft.clientj.state.LoadingState;
import com.ksptool.ourcraft.clientj.state.MainMenuState;

import lombok.extern.slf4j.Slf4j;

/**
 * 状态服务
 * 负责管理项目中的各个状态
 */
@Slf4j
public class ClientStateService {

    private final OurCraftClientJ client;
    private final AppStateManager stateManager;

    private CurrentState currentState;

    //所有的状态都保存在这里
    private MainMenuState mms;
    private LoadingState ls;
    private ArrayTextureVisualizerState atvs;

    public ClientStateService(OurCraftClientJ client) {
        this.client = client;
        this.stateManager = client.getStateManager();
        this.currentState = CurrentState.MAIN_MENU;
        log.info("状态服务初始化完成");
    }

    /**
     * 切换到主菜单状态
     */
    public void toMain(){

        if (mms == null) {
            mms = new MainMenuState(client);
        }

        if(currentState == CurrentState.LOADING){
            stateManager.detach(ls);
        }

        if(currentState == CurrentState.ARRAY_TEXTURE_VISUALIZER){
            stateManager.detach(atvs);
        }

        stateManager.attach(mms);
        currentState = CurrentState.MAIN_MENU;
    }

    /**
     * 加入服务器
     */
    public void joinServer(String host, int port){
        
        if (ls == null) {
            ls = new LoadingState(client);
        }

        if(currentState == CurrentState.MAIN_MENU){
            stateManager.detach(mms);
        }

        stateManager.attach(ls);
        currentState = CurrentState.LOADING;
        ls.setHost(host);
        ls.setPort(port);
    }


    /**
     * 加入世界
     */
    public void joinWorld(){

    }

    /**
     * 显示数组纹理可视化器（调试用）
     */
    public void showArrayTextureVisualizer(){
        if (atvs == null) {
            atvs = new ArrayTextureVisualizerState(client);
        }

        if(currentState == CurrentState.MAIN_MENU){
            stateManager.detach(mms);
        }

        stateManager.attach(atvs);
        currentState = CurrentState.ARRAY_TEXTURE_VISUALIZER;
    }
    
    /**
     * 响应窗口大小变化
     * @param w 窗口宽度
     * @param h 窗口高度
     */
    public void reshape(int w, int h){
        if (currentState == CurrentState.MAIN_MENU) {
            mms.reshape(w, h);
        }
        if (currentState == CurrentState.LOADING) {
            ls.reshape(w, h);
        }
        if (currentState == CurrentState.ARRAY_TEXTURE_VISUALIZER) {
            atvs.reshape(w, h);
        }
    }


    public enum CurrentState {
        MAIN_MENU,
        LOADING,
        IN_GAME,
        TEXTURE_VISUALIZER,
        ARRAY_TEXTURE_VISUALIZER
    }
}
