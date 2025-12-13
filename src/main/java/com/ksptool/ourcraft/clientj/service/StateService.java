package com.ksptool.ourcraft.clientj.service;

import com.jme3.app.state.AppStateManager;
import com.ksptool.ourcraft.clientj.OurCraftClientJ;
import com.ksptool.ourcraft.clientj.state.TextureVisualizerState;
import com.ksptool.ourcraft.clientj.state.LoadingState;
import com.ksptool.ourcraft.clientj.state.MainMenuState;

import lombok.extern.slf4j.Slf4j;

/**
 * 状态服务
 * 负责管理项目中的各个状态
 */
@Slf4j
public class StateService {

    private final OurCraftClientJ client;
    private final AppStateManager stateManager;

    private CurrentState currentState;

    //所有的状态都保存在这里
    private MainMenuState mms;
    private LoadingState ls;
    private TextureVisualizerState tsv;

    public StateService(OurCraftClientJ client) {
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
        } else if(currentState == CurrentState.TEXTURE_VISUALIZER){
            stateManager.detach(tsv);
        }

        stateManager.attach(mms);
        currentState = CurrentState.MAIN_MENU;
    }

    /**
     * 加入服务器
     */
    public void joinServer(){
        if (ls == null) {
            ls = new LoadingState(client);
        }

        if(currentState == CurrentState.MAIN_MENU){
            stateManager.detach(mms);
        }

        stateManager.attach(ls);
        currentState = CurrentState.LOADING;
    }


    /**
     * 加入世界
     */
    public void joinWorld(){

    }

    /**
     * 显示纹理图集可视化器（调试用）
     */
    public void showTextureVisualizer(){
        if (tsv == null) {
            tsv = new TextureVisualizerState(client);
        }

        // 先分离当前状态
        if(currentState == CurrentState.MAIN_MENU){
            stateManager.detach(mms);
        } else if(currentState == CurrentState.LOADING){
            stateManager.detach(ls);
        }

        stateManager.attach(tsv);
        currentState = CurrentState.TEXTURE_VISUALIZER;
    }

    /**
     * 从纹理可视化器返回主菜单
     */
    public void backFromTextureVisualizer(){
        if(currentState == CurrentState.TEXTURE_VISUALIZER){
            stateManager.detach(tsv);
            stateManager.attach(mms);
            currentState = CurrentState.MAIN_MENU;
        }
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
        if (currentState == CurrentState.TEXTURE_VISUALIZER) {
            tsv.reshape(w, h);
        }
    }


    public enum CurrentState {
        MAIN_MENU,
        LOADING,
        IN_GAME,
        TEXTURE_VISUALIZER
    }
}
