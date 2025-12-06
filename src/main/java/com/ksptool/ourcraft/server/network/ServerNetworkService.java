package com.ksptool.ourcraft.server.network;

import com.ksptool.ourcraft.server.OurCraftServer;
import com.ksptool.ourcraft.server.ServerConfigService;
import com.ksptool.ourcraft.server.archive.ArchivePlayerService;
import com.ksptool.ourcraft.server.archive.model.ArchivePlayerDto;
import com.ksptool.ourcraft.server.archive.model.ArchivePlayerVo;
import com.ksptool.ourcraft.server.world.ServerWorld;
import com.ksptool.ourcraft.server.world.ServerWorldService;
import com.ksptool.ourcraft.sharedcore.GlobalService;
import com.ksptool.ourcraft.sharedcore.enums.EngineDefault;
import com.ksptool.ourcraft.sharedcore.network.ndto.AuthNDto;
import com.ksptool.ourcraft.sharedcore.network.nvo.AuthNVo;
import com.ksptool.ourcraft.sharedcore.network.packets.*;

import com.ksptool.ourcraft.sharedcore.utils.position.Pos;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import xyz.downgoon.snowflake.Snowflake;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class ServerNetworkService implements GlobalService {

    //用于生成会话ID的雪花算法
    private final Snowflake snowflake = new Snowflake(1, 1);

    private AtomicBoolean running = new AtomicBoolean(false);

    //所有连接的客户端的会话ID到客户端的映射
    @Getter
    private final ConcurrentHashMap<Long, NetworkSession> sessions = new ConcurrentHashMap<>();

    private final OurCraftServer server;

    private final ServerWorldService worldService;

    private final ServerConfigService configService;

    private final ArchivePlayerService playerArchiveService;

    private final ExecutorService ntp;

    private final String bindAddr;

    private final Integer bindPort; 

    //服务端主Socket
    private ServerSocket serverSocket;

    //网络监听线程
    private Future<?> listenerThread;

    public ServerNetworkService(OurCraftServer server) {
        this.configService = server.getConfigService();
        this.worldService = server.getWorldService();
        this.playerArchiveService = server.getArchiveService().getPlayerService();
        this.ntp = server.getNETWORK_THREAD_POOL();
        this.server = server;
        this.bindAddr = configService.read().getBindAddress();
        this.bindPort = configService.read().getPort();
    }

    /**
     * 启动网络服务
     */
    public void start() {

        if(running.get()){
            log.warn("网络服务当前已在运行,无法重复启动");
            return;
         }
 
         //从线程池中获取一个线程
         listenerThread = ntp.submit(() -> {
             try{
 
                 serverSocket = new ServerSocket(bindPort, EngineDefault.MAX_NETWORK_BACKLOG_SIZE, InetAddress.getByName(bindAddr));
                 running.set(true);
                 log.info("网络服务已就绪，当前端口: {}", bindPort);
 
                 while (true) {
                     
                     if(serverSocket.isClosed()){
                         break;
                     }

                     try {
                         Socket clientSocket = serverSocket.accept();

                         if(sessions.size() >= EngineDefault.MAX_CONCURRENT_SESSIONS){
                            log.warn("达到最大并发会话数限制,拒绝新连接:{}", clientSocket.getRemoteSocketAddress());
                            clientSocket.close();
                            continue;
                         }

                         long sessionId = snowflake.nextId();
                         log.info("新客户端会话: {} 会话ID: {}", clientSocket.getRemoteSocketAddress(), sessionId);
                         NetworkSession nSession = new NetworkSession(this, clientSocket, sessionId);
                         sessions.put(sessionId, nSession);
                         ntp.submit(nSession);
                     } catch (IOException e) {
                         log.warn("接受客户端连接时发生错误: {}", e.getMessage());
                     }
 
                 }
 
                 return null;
             }catch(Exception e){
                 log.error("启动网络监听器失败", e);
                 return null;
             }
         });
    }



    /**
     * 路由网络事件到指定的世界的事件服务中(世界循环将会在每一个Action消费这些事件)
     * @param packet 网络数据包
     */
    public void doRoute(NetworkSession session,Object packet) {

        //处理玩家认证
        if(packet instanceof AuthNDto dto){

            //必须为NEW阶段才能处理认证
            if(!isStage(session,NetworkSession.Stage.NEW)){
                return;
            }

            handleAuth(session, dto);
            return;
        }

        //客户端加载完成后，通知服务端已准备好接收世界数据
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
            session.updateHeartbeat();
            return;
        }

    }

    /**
     * 处理玩家认证
     * @param session 会话
     * @param dto 玩家认证数据包
     */
    private void handleAuth(NetworkSession session,AuthNDto dto) {
        var playerName = dto.playerName();
        var clientVersion = dto.clientVersion();

        log.info("会话:{} 正在请求认证 玩家名称: {} 客户端版本: {}", session.getId(), playerName, clientVersion);

        //查询数据库 获取玩家信息
        ArchivePlayerVo playerVo = playerArchiveService.loadPlayer(playerName);
        var playerDto = new ArchivePlayerDto();

        //玩家不存在，创建新玩家
        if (playerVo == null) {

            //获取服务器默认的世界信息
            ServerWorld defaultWorld = server.getWorldService().getWorld(server.getDefaultWorldName());

            //默认世界未加载或不存在 踢出玩家
            if (defaultWorld == null) {
                log.error("服务器默认世界[{}]未加载或不存在 踢出玩家: {}", server.getDefaultWorldName(), playerName);
                session.sendPacket(AuthNVo.reject("服务器默认世界[" + server.getDefaultWorldName() + "]未加载或不存在"));
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
            log.info("会话:{} 创建新玩家 玩家名称: {}", session.getId(), playerName);
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

        //认证成功 发送认证结果
        session.sendPacket(AuthNVo.accept(session.getId()));
        session.setStage(NetworkSession.Stage.AUTHORIZED);
        log.info("会话:{} 认证成功 玩家名称: {} 客户端版本: {}", session.getId(), playerName, clientVersion);
    }


    /**
     * 处理玩家加入请求
     * @param session 会话
     * @param packet 玩家加入请求数据包
     */
    public void handlePlayerJoin(NetworkSession session,RequestJoinServerNDto packet) {

        var playerName = packet.playerName();

        //查询数据库 获取玩家信息
        ArchivePlayerVo playerVo = playerArchiveService.loadPlayer(playerName);

        var playerDto = new ArchivePlayerDto();

        //玩家不存在，创建新玩家
        if (playerVo == null) {

            //获取服务器默认的世界信息
            ServerWorld defaultWorld = server.getWorldService().getWorld(server.getDefaultWorldName());

            //默认世界未加载或不存在 踢出玩家
            if (defaultWorld == null) {
                log.error("服务器默认世界[{}]未加载或不存在 踢出玩家: {}", server.getDefaultWorldName(), packet.playerName());
                session.sendPacket(RequestJoinServerNVo.reject("服务器默认世界[" + server.getDefaultWorldName() + "]未加载或不存在"));
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
            session.sendPacket(RequestJoinServerNVo.reject("玩家出生世界[" + spawnWorld + "]未加载或不存在"));
            return;
        }

        var chunkPos = spawnPos.toChunkPos(world.getTemplate().getChunkSizeX(), world.getTemplate().getChunkSizeZ());

        //预加载玩家出生点区块(同步)
        world.generateChunkSynchronously(chunkPos.getX(), chunkPos.getZ());
        //接受玩家加入请求
        RequestJoinServerNVo response = new RequestJoinServerNVo(
                1, //0:拒绝, 1:接受
                "玩家加入请求已接受",
                session.getId(),
                (double)spawnPos.getX(),
                (double)spawnPos.getY(),
                (double)spawnPos.getZ(),
                playerVo.getYaw().floatValue(),
                playerVo.getPitch().floatValue());
        session.sendPacket(response);

        //创建玩家的实体 并投入世界中
        session.joinWorld(world, playerVo);
        session.setStage(NetworkSession.Stage.AUTHORIZED);
        return;
    }


    /**
     * 处理客户端加载完成后，通知服务端已准备好接收世界数据
     * @param session 会话
     * @param packet 客户端加载完成后，通知服务端已准备好接收世界数据数据包
     */
    public void handleClientReady(NetworkSession session) {
        session.setStage(NetworkSession.Stage.INVALID);
        return;
    }



    /**
     * 停止网络服务
     */
    public void shutdown() {
        running.set(false);
        //关闭所有客户端
        for(NetworkSession session : sessions.values()){
            session.close();
        }
        //关闭服务器Socket
        if(serverSocket != null && !serverSocket.isClosed()){
            try{
                serverSocket.close();
            }catch(IOException e){
                log.warn("关闭服务器Socket时发生错误: {}", e.getMessage());
            }
        }
        log.info("网络服务已停止");
    }


    /**
     * 检查会话阶段是否为期望的阶段
     * @param session 会话
     * @param stage 期望阶段
     * @return 是否为期望阶段
     */
    private boolean isStage(NetworkSession session,NetworkSession.Stage stage) {
        if(session.getStage() != stage){
            log.warn("出现异常,会话阶段不为期望的阶段,会话ID:{} 期望阶段:{} 当前阶段:{}", session.getId(), stage, session.getStage());
            session.close();
            return false;
        }
        return true;
    }

}
