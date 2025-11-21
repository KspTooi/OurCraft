package com.ksptool.ourcraft.sharedcore;

import org.junit.jupiter.api.Test;

import com.ksptool.ourcraft.sharedcore.config.ServerConfig;


public class ServerConfigTest {


    @Test
    public void testLoadServerConfig() {

        ServerConfig config = ServerConfig.getInstance();

        System.out.println(config.getServerName());
        System.out.println(config.getBindAddress());
        System.out.println(config.getPort());
        System.out.println(config.getSaveName());
        System.out.println(config.getMainWorldName());
    }
    



}
