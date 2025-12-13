package com.ksptool.ourcraft.clientj.network;

import com.ksptool.ourcraft.sharedcore.network.RpcSession;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.net.Socket;
import java.util.concurrent.ExecutorService;

@Slf4j
@Getter
public class ClientNetworkSession extends RpcSession{

    //网络会话阶段
    public enum Stage {
        NEW, // 刚连接，未验证
        AUTHORIZED, // 身份验证通过
        PROCESSED, // 已完成配置
        PROCESS_SWITCHING, // 正在进行进程切换(等待客户端确认)
        PROCESS_SWITCHED, // 已完成进程切换
        IN_WORLD, // 已被投入世界中
        INVALID // 会话无效
    }

    //网络会话ID
    private Long id = null;

    /**
     * -- SETTER --
     *  设置网络会话阶段
     *
     * @param stage 网络会话阶段
     */
    //网络会话阶段
    @Setter
    private volatile Stage stage;

    public ClientNetworkSession(Socket socket, ExecutorService executorService) {
        super(socket, executorService);
        this.stage = Stage.NEW;
    }

    /**
     * 设置网络会话ID 只能设置一次
     * @param id 网络会话ID
     */
    public void setId(Long id) {
        if(this.id != null){
            log.warn("网络会话ID只能设置一次");
            return;
        }
        this.id = id;
    }

}
