package com.ksptool.ourcraft.clientj.network;

import com.ksptool.ourcraft.clientj.service.ClientNetworkService;
import com.ksptool.ourcraft.sharedcore.network.RpcRequest;
import com.ksptool.ourcraft.sharedcore.network.RpcSession;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

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

    //网络会话阶段
    @Setter
    private volatile Stage stage;

    private final ClientNetworkService cns;

    public ClientNetworkSession(ClientNetworkService cns,Socket socket, ExecutorService executorService) {
        super(socket, executorService);
        this.stage = Stage.NEW;
        this.cns = cns;
    }

    /**
     * 读取循环
     */
    public void readLoop() {

        var nr = cns.getNr();

        while (isActive()) {
            //阻塞3分钟获取下一个数据包(这些已经是Kryo解码后的数据包)
            Object packet = receiveNext(3, TimeUnit.MINUTES);
            if(packet == null){
                log.warn("会话:{} 接收数据包超时", id);
                continue;
            }

            //检查是否是RPC请求
            if(packet instanceof RpcRequest<?>(long requestId, Object data)){
                //解包并推送RPC数据
                nr.postRpc(this, data,requestId);
                log.info("会话:{} 收到RPC请求:{}", id, requestId);
                continue;
            }

            //非RPC请求的普通数据包直接在NR中处理
            nr.post(this,packet);
            //log.info("会话:{} 收到普通数据包:{}", id, packet);
        }
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

    /**
     * 检查会话是否活跃
     * 如果会话已经关闭或阶段为INVALID，则返回false
     * @return 是否活跃
     */
    public boolean isActive() {
        if (!super.isActive()){
            return false;
        }
        return stage != Stage.INVALID;
    }
}
