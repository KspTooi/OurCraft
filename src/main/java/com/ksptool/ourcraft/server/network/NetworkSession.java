package com.ksptool.ourcraft.server.network;

import java.io.IOException;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import com.ksptool.ourcraft.server.archive.model.ArchivePlayerVo;
import com.ksptool.ourcraft.server.entity.ServerPlayer;
import com.ksptool.ourcraft.server.world.ServerWorld;
import com.ksptool.ourcraft.sharedcore.network.KryoManager;
import com.ksptool.ourcraft.sharedcore.network.packets.ServerDisconnectNVo;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public class NetworkSession implements Runnable {

    //会话阶段
    public enum Stage{
        NEW,               //NEW(新建): 表示会话刚刚新建
        AUTHORIZED,        //AUTHORIZED(已授权): 表示会话已授权
        CLIENT_READY,      //CLIENT_READY(客户端已准备好): 表示客户端已准备好接收世界数据
        PROCESSING_DATA,   //PROCESSING_DATA(数据处理): 正在同步初始世界数据 (区块等)
        PROCESSING_SWITCH, //PROCESSING_SWITCH(正在进行进程切换): 表示会话正在进行进程切换
        READY,             //READY(就绪): 表示会话已准备好,已加入世界
        INVALID,           //INVALID(无效): 表示会话已无效
    }

    //会话ID
    @Getter
    private final long id;

    //会话阶段
    @Setter
    private volatile Stage stage;

    //客户端版本
    @Setter
    private String clientVersion;

    //连接时间
    private final LocalDateTime connectTime;

    //最后心跳时间
    @Setter
    private LocalDateTime lastHeartbeatTime;

    //Socket
    private final Socket socket;

    //网络服务
    private final ServerNetworkService sns;

    //所在世界
    private final AtomicReference<ServerWorld> world;

    //玩家
    private final AtomicReference<ServerPlayer> entity;

    public NetworkSession(ServerNetworkService sns,Socket socket, long id) {
        this.sns = sns;
        this.id = id;
        this.stage = Stage.NEW;
        this.socket = socket;
        this.connectTime = LocalDateTime.now();
        this.lastHeartbeatTime = LocalDateTime.now();
        this.world = new AtomicReference<>();
        this.entity = new AtomicReference<>();
    }

    @Override
    public void run() {
        while (isActive()) {
            try{
                //接收网络数据包
                Object packet = KryoManager.readObject(socket.getInputStream());
                sns.doRoute(this, packet);
            }catch(Exception e){
                log.error("会话:{} 处理网络数据包时发生错误: {}", id, e.getMessage());
                break;
            }
        }
    }

    /**
     * 检查会话是否活跃
     * @return 是否活跃
     */
    public boolean isActive() {
        return stage != Stage.INVALID;
    }

    /**
     * 关闭会话
     */
    public void close() {
        stage = Stage.INVALID;
        try {

            //如果Socket还未关闭 发送一个断开连接数据包
            if(!socket.isClosed()){
                sendPacket(new ServerDisconnectNVo("会话被服务端关闭"));
            }

            //如果实体还未移除 移除实体
            if(entity.get() != null && getWorld() != null){
                getWorld().removeEntity(entity.get());
            }

            socket.close();
        } catch (IOException e) {
            log.warn("关闭会话:{} 时发生错误: {}", id, e.getMessage());
        }
    }

    /**
     * 加入所在世界
     * @param world 所在世界
     */
    public void joinWorld(ServerWorld world,ArchivePlayerVo playerVo) {

        if (world == null) {
            log.warn("无法为Player会话:{} 加入所在世界 传入的世界为null", id);
            return;
        }
        //设置新世界
        this.world.set(world);

        //创建玩家的实体 并投入世界中
        ServerPlayer newPlayer = new ServerPlayer(world, playerVo, id);
        world.addEntity(newPlayer);
        entity.set(newPlayer);
    }
    
    /**
     * 获取所在世界
     * @return 所在世界
     */
    public ServerWorld getWorld() {
        return world.get();
    }

    /**
     * 向客户端发送数据包
     */
    public void sendPacket(Object packet) {
        if (!isActive() || socket.isClosed()) {
            return;
        }

        if (packet == null) {
            log.warn("尝试发送null数据包");
            return;
        }

        try {
            log.debug("发送数据包到客户端: {}", packet.getClass().getSimpleName());
            KryoManager.writeObject(packet, socket.getOutputStream());
        } catch (IOException e) {
            log.warn("发送数据包到客户端时发生错误: {}", e.getMessage());
            close();
        }
    }


    /**
     * 更新最后心跳时间
     */
    public void updateHeartbeat() {
        lastHeartbeatTime = LocalDateTime.now();
    }


}
