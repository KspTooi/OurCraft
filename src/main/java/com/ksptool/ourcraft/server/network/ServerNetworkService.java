package com.ksptool.ourcraft.server.network;

import com.ksptool.ourcraft.server.OurCraftServer;
import com.ksptool.ourcraft.server.ServerConfigService;
import com.ksptool.ourcraft.server.archive.ArchivePlayerService;
import com.ksptool.ourcraft.server.archive.model.ArchivePlayerDto;
import com.ksptool.ourcraft.server.archive.model.ArchivePlayerVo;
import com.ksptool.ourcraft.server.event.ServerPlayerInputEvent;
import com.ksptool.ourcraft.server.world.ServerWorld;
import com.ksptool.ourcraft.server.world.ServerWorldService;
import com.ksptool.ourcraft.sharedcore.utils.FlexChunkSerializer;
import com.ksptool.ourcraft.sharedcore.GlobalService;
import com.ksptool.ourcraft.sharedcore.enums.EngineDefault;
import com.ksptool.ourcraft.sharedcore.network.RpcRequest;
import com.ksptool.ourcraft.sharedcore.network.RpcResponse;
import com.ksptool.ourcraft.sharedcore.network.ndto.AuthNDto;
import com.ksptool.ourcraft.sharedcore.network.ndto.BatchDataFinishNDto;
import com.ksptool.ourcraft.sharedcore.network.ndto.PsAllowNDto;
import com.ksptool.ourcraft.sharedcore.network.ndto.PsFinishNDto;
import com.ksptool.ourcraft.sharedcore.network.nvo.AuthNVo;
import com.ksptool.ourcraft.sharedcore.network.nvo.BatchDataNVo;
import com.ksptool.ourcraft.sharedcore.network.nvo.PsJoinWorldNVo;
import com.ksptool.ourcraft.sharedcore.network.nvo.PsChunkNVo;
import com.ksptool.ourcraft.sharedcore.network.nvo.PsNVo;
import com.ksptool.ourcraft.sharedcore.network.nvo.PsPlayerNVo;
import com.ksptool.ourcraft.sharedcore.network.packets.*;
import com.ksptool.ourcraft.sharedcore.utils.position.ChunkPos;
import com.ksptool.ourcraft.sharedcore.utils.position.PrecisionPos;
import com.ksptool.ourcraft.sharedcore.utils.viewport.ChunkViewPort;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import xyz.downgoon.snowflake.Snowflake;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class ServerNetworkService implements GlobalService {

    //用于生成会话ID的雪花算法
    private final Snowflake snowflake = new Snowflake(1, 1);

    private final AtomicBoolean running = new AtomicBoolean(false);

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
    @Getter
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
        if(packet instanceof RpcRequest(long requestId, Object data)){

            if(data instanceof AuthNDto authDto){
                //处理认证
                if(!handleAuth(session, authDto, requestId)){
                    return;
                }

                //处理批数据同步
                if(!handleBatchData(session)){
                    return;
                }
            } 

            return;
        }

        //客户端已确认批数据
        if(packet instanceof BatchDataFinishNDto dto){

            //通知客户端需要进行进程切换
            if(!handleInitProcessSwitch(session, dto)){
                return;
            } 

            return;
        }

        //客户端确认进程切换
        if(packet instanceof PsAllowNDto dto){
            if(!handleProcessSwitchAllow(session, dto)){
                return;
            }
            return;
        }

        //客户端进程切换完成
        if(packet instanceof PsFinishNDto dto){
            if(!handleProcessSwitchFinish(session, dto)){
                return;
            }
            return;
        }

        if (packet instanceof PlayerInputStateNDto dto) {

            //路由到对应世界
            var world = session.getWorld();

            if(world == null){
                log.error("会话:{} 无法处理玩家输入事件,玩家所在世界不存在", session.getId());
                return;
            }

            world.getSweb().publish(new ServerPlayerInputEvent(session.getId(), dto.w(), dto.s(), dto.a(), dto.d(), dto.space(), dto.shift(), dto.yaw(), dto.pitch()));
            return;
        }

        //暂时不处理玩家动作 未完成协议
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
     * @return 是否认证成功
     */
    private boolean handleAuth(NetworkSession session,AuthNDto dto, long requestId) {

        //必须为NEW阶段才能处理认证
        if(!isStage(session,NetworkSession.Stage.NEW)){
            return false;
        }

        var playerName = dto.playerName();
        var clientVersion = dto.clientVersion();

        log.info("会话:{} 正在请求认证 玩家名称: {} 客户端版本: {}", session.getId(), playerName, clientVersion);

        //查询数据库 获取玩家信息
        ArchivePlayerVo playerVo = playerArchiveService.loadPlayer(playerName);
        var playerDto = new ArchivePlayerDto();

        //玩家不存在，创建新玩家
        if (playerVo == null) {

            //获取服务器默认的世界信息
            ServerWorld defaultWorld = worldService.getWorld(server.getDefaultWorldName());

            //默认世界未加载或不存在 踢出玩家
            if (defaultWorld == null) {
                log.error("服务器默认世界[{}]未加载或不存在 踢出玩家: {}", server.getDefaultWorldName(), playerName);
                session.sendPacket(AuthNVo.reject("服务器默认世界[" + server.getDefaultWorldName() + "]未加载或不存在"));
                return false;
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

        //认证成功 发送认证结果(需要包装为RpcResponse)
        var rpcResponse = RpcResponse.of(requestId, AuthNVo.accept(session.getId()));
        session.sendPacket(rpcResponse);
        session.setStage(NetworkSession.Stage.AUTHORIZED);
        session.setArchive(playerVo);
        log.info("会话:{} 认证成功 玩家名称: {} 客户端版本: {}", session.getId(), playerName, clientVersion);
        return true;
    }

    
    /**
     * 处理批数据同步
     * @param session 会话
     * @return 是否同步成功
     */
    public boolean handleBatchData(NetworkSession session) {

        //必须为AUTHORIZED阶段才能处理批数据同步
        if(!isStage(session,NetworkSession.Stage.AUTHORIZED)){
            return false;
        }

        var archive = session.getArchive();

        if(archive == null){
            log.error("会话:{} 无法同步批数据,玩家归档数据不存在", session.getId());
            session.close("无法同步批数据,玩家归档数据不存在!");
            return false;
        }

        log.info("会话:{} 玩家:{} 正在同步批数据", session.getId(), archive.getName());

        //发送批数据(测试用，正式上线时需要根据实际情况发送)
        session.sendPacket(BatchDataNVo.of(-1, "BatchData".getBytes()));
        return true;
    }

    /**
     * 通知客户端需要进行进程切换
     * @param session 会话
     * @param dto 通知客户端需要进行进程切换数据包
     * @return 是否处理成功
     */
    public boolean handleInitProcessSwitch(NetworkSession session,BatchDataFinishNDto dto){

        //必须为AUTHORIZED阶段才能处理通知客户端需要进行进程切换
        if(!isStage(session,NetworkSession.Stage.AUTHORIZED)){
            session.close("无法开始进程切换,当前会话阶段不为期望的阶段!");
            return false;
        }
        session.setStage(NetworkSession.Stage.PROCESSED);

        var archive = session.getArchive();

        if(archive == null){
            log.error("会话:{} 无法进行进程切换,玩家归档数据不存在", session.getId());
            session.close("无法进行进程切换,玩家归档数据不存在!");
            return false;
        }

        //查询玩家所在世界
        ServerWorld world = worldService.getWorld(archive.getWorldName());

        if(world == null){
            log.error("会话:{} 玩家:{} 无法进行进程切换,玩家所在世界不存在", session.getId(), archive.getName());
            session.close("无法进行进程切换,玩家所在世界不存在!");
            return false;
        }

        //获取玩家落地位置
        var groundPos = PrecisionPos.of(archive.getPosX(), archive.getPosY(), archive.getPosZ());
        var chunkPos = groundPos.toChunkPos(world.getTemplate().getChunkSizeX(), world.getTemplate().getChunkSizeZ());

        //在玩家落地位置的3X3签发永久租约
        ChunkViewPort vp = ChunkViewPort.of(chunkPos,1);
        vp.setMode(0);
        Set<ChunkPos> chunkPosSet = vp.getChunkPosSet();
        log.info("会话:{} 玩家:{} 为玩家准备落地区块 总数:{}", session.getId(), archive.getName(),chunkPosSet.size());

        for(var item : chunkPosSet){
            world.getFcls().issuePermanentLease(item, archive.getId());
            //加载玩家落地位置区块(网络线程会等待区块加载完成后才能进行下一步)
            var future = world.getFscs().loadOrGenerate(item);

            //等待区块加载完成(超时5分钟)
            try {
                future.get(5, TimeUnit.MINUTES);
            } catch (Exception e) {
                log.error("会话:{} 玩家:{} 无法进行进程切换,落地区块加载失败", session.getId(), archive.getName());
                session.close("无法进行进程切换,区块加载失败!");
                return false;
            }

        }

        var aps = world.getTemplate().getActionPerSecond();
        var totalActions = world.getSwts().getTotalActions();
        var startDateTime = world.getTemplate().getStartDateTime();

        //区块加载完成 通知客户端可以开始进程切换
        session.sendPacket(PsNVo.of(world.getName(), aps, totalActions, startDateTime));
        log.info("会话:{} 玩家:{} 开始进程切换", session.getId(), archive.getName());
        session.setStage(NetworkSession.Stage.PROCESS_SWITCHING);

        //等待客户端完成进程切换(收到PsAllowNDto数据包)
        return true;
    }

    /**
     * 处理客户端确认进程切换
     * @param session 会话
     * @param dto 客户端确认进程切换数据包
     * @return 是否处理成功
     */
    public boolean handleProcessSwitchAllow(NetworkSession session,PsAllowNDto dto){

        //必须为PROCESS_SWITCHING阶段才能处理客户端确认进程切换
        if(!isStage(session,NetworkSession.Stage.PROCESS_SWITCHING)){
            session.close("无法发送进程切换数据,当前会话阶段不为期望的阶段!");
            return false;
        }

        var archive = session.getArchive();

        if(archive == null){
            log.error("会话:{} 无法发送进程切换数据,玩家归档数据不存在", session.getId());
            session.close("无法发送进程切换数据,玩家归档数据不存在!");
            return false;
        }
        
        //发送进程切换数据(世界区块数据、玩家数据、周围其他实体数据)
        var groundPos = session.getArchive().getGroundPos();
        var groundWorld = archive.getWorldName();

        //查询世界
        ServerWorld world = worldService.getWorld(groundWorld);

        if(world == null){
            log.error("会话:{} 无法发送进程切换数据,玩家落地世界不存在", session.getId());
            session.close("无法发送进程切换数据,玩家落地世界不存在!");
            return false;
        }

        //获取玩家落地位置区块数据
        var groundChunkPos = groundPos.toChunkPos(world.getTemplate().getChunkSizeX(), world.getTemplate().getChunkSizeZ());
        var groundChunk = world.getFscs().loadOrGenerate(groundChunkPos);

        try {

            //发送区块数据
            var chunkData = groundChunk.get(16, TimeUnit.SECONDS);
            var serializedData = FlexChunkSerializer.serialize(chunkData.getBlockData());
            session.sendPacket(PsChunkNVo.of(groundChunkPos.getX(), groundChunkPos.getZ(), serializedData));

            //发送玩家数据
            var uuid = archive.getUuid();
            var name = archive.getName();
            var health = archive.getHealth();
            var hungry = archive.getHungry();
            var posX = archive.getPosX();
            var posY = archive.getPosY();
            var posZ = archive.getPosZ();
            var yaw = archive.getYaw();
            var pitch = archive.getPitch();
            session.sendPacket(PsPlayerNVo.of(uuid, name, health, hungry, posX, posY, posZ, yaw, pitch));
            log.info("会话:{} 玩家:{} 发送进程切换数据", session.getId(), archive.getName());

        } catch (Exception e) {
            log.error("会话:{} 无法发送进程切换数据,玩家落地位置区块加载失败", session.getId());
            session.close("无法发送进程切换数据,玩家落地位置区块加载失败!");
            return false;
        }

        return true;
    }

    /**
     * 处理客户端完成进程切换
     * @param session 会话
     * @param dto 客户端完成进程切换数据包
     * @return 是否处理成功
     */
    public boolean handleProcessSwitchFinish(NetworkSession session,PsFinishNDto dto){

        //必须为PROCESS_SWITCHING阶段才能处理客户端完成进程切换
        if(!isStage(session,NetworkSession.Stage.PROCESS_SWITCHING)){
            session.close("无法完成进程切换,当前会话阶段不为期望的阶段!");
            return false;
        }

        var archive = session.getArchive();

        if(archive == null){
            log.error("会话:{} 无法完成进程切换,玩家归档数据不存在", session.getId());
            session.close("无法完成进程切换,玩家归档数据不存在!");
            return false;
        }

        //查询玩家所在世界
        ServerWorld world = worldService.getWorld(archive.getWorldName());

        if(world == null){
            log.error("会话:{} 无法完成进程切换,玩家所在世界不存在", session.getId());
            session.close("无法完成进程切换,玩家所在世界不存在!");
            return false;
        }

        //投入玩家到世界
        session.joinWorld(world, archive);
        session.setStage(NetworkSession.Stage.IN_WORLD);
        //通知客户端
        session.sendPacket(new PsJoinWorldNVo());
        log.info("会话:{} 玩家:{} 完成进程切换 已加入世界:{}", session.getId(), archive.getName(), world.getName());
        return true;
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
