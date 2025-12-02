package com.ksptool.ourcraft;

import com.ksptool.ourcraft.client.GameClient;
import lombok.extern.slf4j.Slf4j;


/**
 * 程序入口类，负责启动游戏并管理GameServer和GameClient的生命周期
 */
@Slf4j
public class ClientLauncher {

    void main() {
        GameClient gameClient = new GameClient();
        gameClient.run();
    }

}
