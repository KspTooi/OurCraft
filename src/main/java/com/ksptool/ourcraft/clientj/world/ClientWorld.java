package com.ksptool.ourcraft.clientj.world;

import com.ksptool.ourcraft.clientj.OurCraftClientJ;
import com.ksptool.ourcraft.clientj.entity.ClientPlayer;
import com.ksptool.ourcraft.clientj.world.chunk.FlexClientChunkService;
import com.ksptool.ourcraft.sharedcore.Registry;
import com.ksptool.ourcraft.sharedcore.world.SharedWorld;
import com.ksptool.ourcraft.sharedcore.world.WorldTemplate;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;

@Slf4j
public class ClientWorld implements SharedWorld {

    @Getter@Setter
    private WorldTemplate template;

    @Getter@Setter
    private String name;

    @Getter
    private ClientPlayer player;

    @Getter
    private FlexClientChunkService fccs;

    @Getter
    private final OurCraftClientJ client;

    public ClientWorld(OurCraftClientJ client) {
        this.client = client;
    }

    /**
     * 初始化世界(每次进程切换时调用,这会清除所有旧的场景，并重新创建新的场景)
     * @param worldName 世界名称
     * @param worldTemplateRegName 世界模板注册名
     * @param aps 每秒动作数
     * @param totalActions 总动作数
     * @param startDateTime 开始时间
     */
    public void init(String worldName, String worldTemplateRegName, int aps, long totalActions, LocalDateTime startDateTime) {
        this.name = worldName;
        
        WorldTemplate template = Registry.getInstance().getWorldTemplate(worldTemplateRegName);
        if (template == null) {
            throw new IllegalArgumentException("无法初始化世界: " + worldName + " 因为世界模板未注册: " + worldTemplateRegName);
        }
        this.template = template;
        
        if (client != null && this.fccs == null) {
            this.fccs = new FlexClientChunkService(client, this);
        }
        
        log.info("世界初始化完成 世界名称:{} 模板:{} APS:{} 总动作数:{} 开始时间:{}", worldName, worldTemplateRegName, aps, totalActions, startDateTime);
    }

    /**
     * 设置玩家
     * @param player 玩家对象
     */
    public void setPlayer(ClientPlayer player) {
        this.player = player;
        log.info("设置客户端玩家: {}", player != null ? player.getName() : "null");
    }

    /**
     * 世界动作
     * @param delta 时间差 由CWEU传入
     */
    @Override
    public void action(double delta) {

        //处理网络事件


        //提交MESH计算任务
        

        //处理已完成的区块MESH计算



    }


    @Override
    public boolean isServerSide() {
        return false;
    }

    @Override
    public boolean isClientSide() {
        return true;
    }

}
