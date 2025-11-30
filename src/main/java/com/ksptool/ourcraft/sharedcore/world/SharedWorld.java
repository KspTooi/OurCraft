package com.ksptool.ourcraft.sharedcore.world;

public interface SharedWorld {

    /**
     * 是否是服务端
     * @return 是否是服务端
     */
    boolean isServerSide();

    /**
     * 是否是客户端
     * @return 是否是客户端
     */
    boolean isClientSide();

    /**
     * 用于驱动世界逻辑的执行
     */
    void action();

}
