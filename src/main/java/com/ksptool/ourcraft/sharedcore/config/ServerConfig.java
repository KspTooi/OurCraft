package com.ksptool.ourcraft.sharedcore.config;


import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * 服务器配置类 用于存储服务器配置信息
 */
@Slf4j
@Getter@Setter
public class ServerConfig {

    //服务器名称
    private String serverName;

    //绑定地址
    private String bindAddress;

    //端口
    private int port;

    //存档名称
    private String saveName;

    //主世界名称
    private String mainWorldName;

}
