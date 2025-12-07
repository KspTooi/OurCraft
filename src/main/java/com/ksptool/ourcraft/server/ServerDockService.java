package com.ksptool.ourcraft.server;

import lombok.extern.slf4j.Slf4j;

/**
 * 服务端Dock服务
 * Dock用来处理那些不属于World的事件,因为在Ourcraft的架构设计中，每一个World都通过SWEU进行驱动 它们是多线程的,所以不能在World中处理那些全局事件(例如玩家登录授权,服务器启动、世界初始化等)
 */
@Slf4j
public class ServerDockService {

    

}
