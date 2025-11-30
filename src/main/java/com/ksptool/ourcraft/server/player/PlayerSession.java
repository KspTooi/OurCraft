package com.ksptool.ourcraft.server.player;

import com.ksptool.ourcraft.server.OurCraftServer;
import com.ksptool.ourcraft.server.archive.ArchivePlayerService;
import com.ksptool.ourcraft.server.archive.model.ArchivePlayerDto;
import com.ksptool.ourcraft.server.archive.model.ArchivePlayerVo;
import com.ksptool.ourcraft.server.entity.ServerPlayer;
import com.ksptool.ourcraft.server.world.ServerWorld;
import com.ksptool.ourcraft.sharedcore.enums.EngineDefault;
import com.ksptool.ourcraft.sharedcore.network.KryoManager;
import com.ksptool.ourcraft.sharedcore.network.packets.ClientKeepAliveNPkg;
import com.ksptool.ourcraft.sharedcore.network.packets.ClientReadyNDto;
import com.ksptool.ourcraft.sharedcore.network.packets.PlayerDActionNDto;
import com.ksptool.ourcraft.sharedcore.network.packets.PlayerDcparNDto;
import com.ksptool.ourcraft.sharedcore.network.packets.PlayerDshsNdto;
import com.ksptool.ourcraft.sharedcore.network.packets.PlayerInputStateNDto;
import com.ksptool.ourcraft.sharedcore.network.packets.RequestJoinServerNDto;
import com.ksptool.ourcraft.sharedcore.network.packets.RequestJoinServerNVo;
import com.ksptool.ourcraft.sharedcore.utils.position.Pos;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.UUID;


/**
 * 玩家会话，每个玩家会话对应一个客户端连接
 * 在虚拟线程中运行，负责处理单个玩家的所有网络交互
 */
@Slf4j
public class PlayerSession implements Runnable {

    private final ArchivePlayerService playerArchiveService;

    private final OurCraftServer server;

    //会话ID
    @Getter
    private final long sessionId;

    //玩家名称
    private String playerName;

    //客户端版本
    private String clientVersion;

    //连接时间  
    private LocalDateTime firstConnectTime;

    //最后心跳时间
    private LocalDateTime lastHeartbeatTime;

    //会话生命周期
    @Getter
    private Stage stage;

    private final Socket socket;
    private final InputStream is;
    private final OutputStream os;

    private volatile boolean running = true;
    
    @Getter
    @Setter
    private ServerPlayer player;
    
    @Getter
    @Setter
    private Integer lastChunkX;
    
    @Getter
    @Setter
    private Integer lastChunkZ;
    
    @Getter
    @Setter
    private volatile boolean playerInitialized = false;

    private final Object sendLock = new Object();
    
    public PlayerSession(Socket socket, OurCraftServer server, long sessionId) throws IOException {
        this.socket = socket;
        this.server = server;
        this.is = socket.getInputStream();
        this.os = socket.getOutputStream();
        this.sessionId = sessionId;
        this.playerArchiveService = server.getArchiveService().getPlayerService();
    }
    
    @Override
    public void run() {

        log.info("客户端连接已建立: {}", socket.getRemoteSocketAddress());
        stage = Stage.NEW;

        try {
            while (running && !socket.isClosed()) {
                try {
                    // 从输入流读取数据包
                    Object packet = KryoManager.readObject(is);

                    //由网络线程内部处理数据包
                    handlePacket(packet);
                    
                    // 将数据包分发给GameServer处理
                    server.handlePacket(this, packet);
                } catch (IOException e) {
                    if (running) {
                        log.warn("读取客户端数据包时发生错误: {}", e.getMessage());
                    }
                    break;
                } catch (Exception e) {
                    log.error("处理客户端数据包时发生错误", e);
                    break;
                }
            }
        } finally {
            close();
        }
        
        log.info("客户端连接已关闭: {}", socket.getRemoteSocketAddress());
    }
    
    /**
     * 用于网络线程内部处理数据包
     * @param packet 数据包
     */
    private void handlePacket(Object packet) {

        //玩家加入请求
        if (packet instanceof RequestJoinServerNDto(String version, String name)) {

            stage = Stage.PROCESSING_DATA;
            playerName = name;
            clientVersion = version;
            firstConnectTime = LocalDateTime.now();

            //查询数据库 获取玩家信息
            ArchivePlayerVo playerVo = playerArchiveService.loadPlayer(playerName);

            var playerDto = new ArchivePlayerDto();

            //玩家不存在，创建新玩家
            if (playerVo == null) {

                //获取服务器默认的世界信息
                ServerWorld defaultWorld = server.getWorldService().getWorld(server.getDefaultWorldName());

                //默认世界未加载或不存在 踢出玩家
                if (defaultWorld == null) {
                    log.error("服务器默认世界未加载或不存在 踢出玩家: {}", name);
                    rejectJoinRequest("服务器默认世界未加载或不存在");
                    return;
                }

                //获取世界默认出生点
                var spawnPos = defaultWorld.getDefaultSpawnPos();

                playerDto.setUuid(UUID.randomUUID().toString());
                playerDto.setName(playerName);
                playerDto.setLoginCount(1);
                playerDto.setLastLoginTime(LocalDateTime.now());
                playerDto.setWorldName(defaultWorld.getName());
                playerDto.setPosX((double)spawnPos.getX());
                playerDto.setPosY((double)spawnPos.getY());
                playerDto.setPosZ((double)spawnPos.getZ());
                playerDto.setYaw(0.0);
                playerDto.setPitch(0.0);
                playerDto.setHealth(40);
                playerDto.setHungry(40);
                playerDto.setExp(0L);
                playerArchiveService.savePlayer(playerDto);
            }

            //玩家存在 更新必要字段
            if (playerVo != null) {
                playerDto.setUuid(playerVo.getUuid());
                playerDto.setName(playerVo.getName());
                playerDto.setLoginCount(playerVo.getLoginCount() + 1);
                playerDto.setLastLoginTime(LocalDateTime.now());
                playerDto.setWorldName(playerVo.getWorldName());
                playerDto.setPosX(playerVo.getPosX());
                playerDto.setPosY(playerVo.getPosY());
                playerDto.setPosZ(playerVo.getPosZ());
                playerDto.setYaw(playerVo.getYaw());
                playerDto.setPitch(playerVo.getPitch());
                playerDto.setHealth(playerVo.getHealth());
                playerDto.setHungry(playerVo.getHungry());
                playerDto.setExp(playerVo.getExp());
                playerArchiveService.savePlayer(playerDto);
            }

            //查询最新的玩家信息
            playerVo = playerArchiveService.loadPlayer(playerName);

            //准备玩家出生点
            var spawnWorld = playerVo.getWorldName();
            var spawnPos = Pos.of(playerVo.getPosX().intValue(), playerVo.getPosY().intValue(), playerVo.getPosZ().intValue());

            //获取玩家出生世界
            var world = server.getWorldService().getWorld(spawnWorld);

            if (world == null) {
                log.error("玩家出生世界未加载或不存在: {}", spawnWorld);
                rejectJoinRequest("玩家出生世界未加载或不存在");
                return;
            }

            var chunkPos = spawnPos.toChunkPos(world.getTemplate().getChunkSizeX(), world.getTemplate().getChunkSizeZ());

            //预加载玩家出生点区块(同步)
            world.generateChunkSynchronously(chunkPos.getX(), chunkPos.getZ());
            //接受玩家加入请求
            RequestJoinServerNVo response = new RequestJoinServerNVo(
                    1, //0:拒绝, 1:接受
                    "玩家加入请求已接受",
                    sessionId,
                    (double)spawnPos.getX(),
                    (double)spawnPos.getY(),
                    (double)spawnPos.getZ(),
                    playerVo.getYaw().floatValue(),
                    playerVo.getPitch().floatValue());
            sendPacket(response);


            //创建玩家的实体 并投入世界中
            ServerPlayer newPlayer = new ServerPlayer(world, playerVo, sessionId);
            world.addEntity(newPlayer);
            this.player = newPlayer;
            return;
        }

        if (packet instanceof ClientReadyNDto) {
            return;
        }

        if (packet instanceof PlayerInputStateNDto) {
            return;
        }

        if (packet instanceof PlayerDcparNDto) {
            return;
        }

        if (packet instanceof PlayerDshsNdto) {
            return;
        }

        if (packet instanceof PlayerDActionNDto) {
            return;
        }

        //心跳包，更新最后心跳时间
        if (packet instanceof ClientKeepAliveNPkg) {
            lastHeartbeatTime = LocalDateTime.now();
            return;
        }

    }



    /**
     * 向客户端发送数据包
     */
    public void sendPacket(Object packet) {
        if (!running || socket.isClosed()) {
            return;
        }
        
        if (packet == null) {
            log.warn("尝试发送null数据包");
            return;
        }
        
        synchronized (sendLock) {
        try {
            log.debug("发送数据包到客户端: {}", packet.getClass().getSimpleName());
            KryoManager.writeObject(packet, os);
        } catch (IOException e) {
            log.warn("发送数据包到客户端时发生错误: {}", e.getMessage());
            close();
            }
        }
    }
    
    /**
     * 关闭连接
     */
    public void close() {
        if (!running) {
            return;
        }
        
        running = false;
        
        // 如果有关联的玩家实体，通知GameServer移除
        if (player != null && server != null) {
            server.onClientDisconnected(this);
        }
        
        try {
            socket.close();
        } catch (IOException e) {
            log.warn("关闭客户端连接时发生错误: {}", e.getMessage());
        }
    }
    
    /**
     * 检查连接是否活跃
     */
    public boolean isConnected() {
        return running && !socket.isClosed();
    }


    /**
     * 玩家会话生命周期
     */
    public enum Stage{

        /**
         * NEW(新建): 表示会话刚刚新建
         */
        NEW,

        /**
         * PROCESSING_DATA(数据处理): 正在同步初始世界数据 (区块等)
         */
        PROCESSING_DATA,

        /**
         * READY(就绪): 表示会话已准备好,玩家已经加入世界
         */
        READY

    }

    /**
     * 拒绝玩家加入请求
     * @param reason 拒绝原因
     */
    public void rejectJoinRequest(String reason) {
        RequestJoinServerNVo response = new RequestJoinServerNVo(
                0, //0:拒绝, 1:接受
                reason,
                null,
                null,
                null,
                null,
                null, null);
        sendPacket(response); 
    }


}

