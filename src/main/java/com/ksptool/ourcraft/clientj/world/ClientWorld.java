package com.ksptool.ourcraft.clientj.world;

import com.ksptool.ourcraft.sharedcore.world.SharedWorld;
import com.ksptool.ourcraft.sharedcore.world.WorldTemplate;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClientWorld implements SharedWorld {

    @Getter@Setter
    private WorldTemplate template;

    @Getter@Setter
    private String name;




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
