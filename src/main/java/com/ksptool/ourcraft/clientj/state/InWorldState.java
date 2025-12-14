package com.ksptool.ourcraft.clientj.state;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.ksptool.ourcraft.clientj.OurCraftClientJ;
import com.ksptool.ourcraft.clientj.commons.event.ChunkLoadEvent;
import com.ksptool.ourcraft.clientj.commons.event.ChunkUnloadEvent;
import com.ksptool.ourcraft.clientj.commons.event.PlayerLocationUpdateEvent;
import com.ksptool.ourcraft.clientj.entity.ClientPlayer;
import com.ksptool.ourcraft.clientj.world.ClientWorld;
import com.ksptool.ourcraft.clientj.service.ClientEventService;
import com.ksptool.ourcraft.clientj.service.ClientNetworkService;
import com.ksptool.ourcraft.clientj.service.ClientStateService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class InWorldState extends BaseAppState {

    //客户端
    private OurCraftClientJ client;

    //世界
    private ClientWorld world;

    //事件服务
    private ClientEventService ces;

    //状态服务
    private ClientStateService css;

    //网络服务
    private ClientNetworkService cns;

    public InWorldState(OurCraftClientJ client) {
        this.client = client;
    }

    @Override
    protected void initialize(Application application) {
        this.world = client.getWorld();
        this.ces = client.getCes();
        this.css = client.getCss();
        this.cns = client.getCns();

        //注册必要的事件监听器
        ces.subscribe(ChunkLoadEvent.class, this::onChunkLoad);
        ces.subscribe(ChunkUnloadEvent.class, this::onChunkUnload);
        ces.subscribe(PlayerLocationUpdateEvent.class, this::onPlayerLocationUpdate);
    }



    @Override
    protected void onEnable() {

        //启动CWEU
        client.startCWEU();

        //将世界节点添加到场景中
        client.getRootNode().attachChild(world.getWorldNode());
        log.info("进入世界中状态");
    }


    @Override
    protected void cleanup(Application application) {
        if (client != null && client.getWorld() != null && client.getWorld().getWorldNode() != null) {
            client.getRootNode().detachChild(client.getWorld().getWorldNode());
        }
    }


    @Override
    protected void onDisable() {
        if (client != null && client.getWorld() != null && client.getWorld().getWorldNode() != null) {
            client.getRootNode().detachChild(client.getWorld().getWorldNode());
            log.info("离开世界中状态");
        }
    }

    
    @Override
    public void update(float tpf) {
        if (client != null && client.getWorld() != null && client.getWorld().getFccs() != null) {
            client.getWorld().getFccs().update(tpf);
        }
    }

    /**
     * 处理接收服务端反馈区块加载网络包
     * @param event 区块加载事件
     */
    public void onChunkLoad(ChunkLoadEvent event) {
        world.getFccs().addChunkFromRawData(event.getChunkPos(), event.getBlockData());
    }

    /**
     * 处理接收服务端反馈区块卸载网络包
     * @param event 区块卸载事件
     */
    public void onChunkUnload(ChunkUnloadEvent event) {

    }

    /**
     * 处理接收服务端反馈玩家位置网络包
     * @param event 玩家位置更新事件
     */
    public void onPlayerLocationUpdate(PlayerLocationUpdateEvent event) {

    }

}
