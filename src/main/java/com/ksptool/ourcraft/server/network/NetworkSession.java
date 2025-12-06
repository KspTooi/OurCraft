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

/**
 * 网络会话，每个网络会话对应一个客户端连接
 * 在虚拟线程中运行，负责处理单个客户端的所有网络交互
 * <p>
 * 玩家加入与进程切换流程
 * 1 [C] 发起TCP
 * 1 [S] 接受TCP,创建NetworkSession 分配SessionID 状态为NEW
 * 2 [C] 发送认证包(AuthNDto) 携带玩家名称 客户端版本
 * 2 [S] 处理认证包 验证玩家名称,客户端版本 验证通过后 状态为AUTHORIZED
 * 2 [C] 期望认证结果包(AuthNVo)
 * <p>
 * 3 [S] 服务端发送配置批数据(BatchDataNVo 例如调色板、世界模板等数据 !现在暂时不做)
 * 3 [C] 发送批数据确认(BatchDataFinishNDto) 状态变更为 PROCESSED
 * <p>
 * 4 [S] 通知客户端需要进程切换(PsNVo 携带目标世界的参数(世界|APS))
 * 4 [C] 准备本地资源 完成后发送(PsAllowNDto)
 * 4 [S] 发送进程切换数据 1.世界数据(区块数据) 2.玩家数据(位置|背包|血量|经验)  3.周围其他实体数据
 * 4 [C] 接收进程切换数据 准备本地资源 完成后发送(PsFinishNDto) 状态变更PROCESS_SWITCHED 等待被投入世界中
 * 4 [S] 投入玩家到世界,并广播给视口在范围内的其他玩家
 */
@Slf4j
@Getter
public class NetworkSession implements Runnable {

    //会话阶段
    public enum Stage{
        NEW,                 // 刚连接，未验证
        AUTHORIZED,          // 身份验证通过
        PROCESSED,           // 已完成配置
        PROCESS_SWITCHED,    // 已完成进程切换
        IN_WORLD,            // 已被投入世界中
        INVALID              // 会话无效
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
