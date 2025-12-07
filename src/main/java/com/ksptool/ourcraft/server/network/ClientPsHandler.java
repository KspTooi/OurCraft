package com.ksptool.ourcraft.server.network;

import com.ksptool.ourcraft.server.OurCraftServer;
import com.ksptool.ourcraft.server.archive.ArchivePlayerService;
import com.ksptool.ourcraft.server.archive.ArchiveService;
import com.ksptool.ourcraft.server.archive.model.ArchivePlayerDto;
import com.ksptool.ourcraft.server.archive.model.ArchivePlayerVo;
import com.ksptool.ourcraft.server.world.ServerWorld;
import com.ksptool.ourcraft.server.world.ServerWorldService;
import com.ksptool.ourcraft.sharedcore.network.ndto.AuthRpcDto;
import com.ksptool.ourcraft.sharedcore.network.ndto.BatchDataFinishNDto;
import com.ksptool.ourcraft.sharedcore.network.ndto.PsAllowNDto;
import com.ksptool.ourcraft.sharedcore.network.ndto.PsFinishNDto;
import com.ksptool.ourcraft.sharedcore.network.nvo.*;
import com.ksptool.ourcraft.sharedcore.utils.FlexChunkSerializer;
import com.ksptool.ourcraft.sharedcore.utils.position.ChunkPos;
import com.ksptool.ourcraft.sharedcore.utils.position.PrecisionPos;
import com.ksptool.ourcraft.sharedcore.utils.viewport.ChunkViewPort;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
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
 * 4 [S] 发送进程切换数据 1.世界数据(区块数据) 2.玩家数据(位置|背包|血量|经验) 3.周围其他实体数据
 * 4 [C] 接收进程切换数据 准备本地资源 完成后发送(PsFinishNDto) 状态变更PROCESS_SWITCHED 等待被投入世界中
 * 4 [S] 投入玩家到世界,并广播给视口在范围内的其他玩家 完成后发送(PsInWorldNDto) 状态变更IN_WORLD
 */
@Slf4j
public class ClientPsHandler {

    private final ServerNetworkService sns;

    private final NetworkRouter nr;

    private final ArchiveService as;

    private final ArchivePlayerService aps;

    private final ServerWorldService sws;

    private final OurCraftServer server;

    public ClientPsHandler(ServerNetworkService sns) {
        this.sns = sns;
        this.nr = sns.getNr();
        this.server = sns.getServer();
        as = sns.getServer().getArchiveService();
        aps = as.getPlayerService();
        sws = sns.getServer().getWorldService();
        nr.subscribeRpc(AuthRpcDto.class,this::playerAuth);
    }

    /**
     * 玩家认证
     *
     * @param session 会话
     * @param dto     玩家认证数据包
     */
    public void playerAuth(NetworkSession session, AuthRpcDto dto,long rpcId) {

        if(!session.isStage(NetworkSession.Stage.NEW)){
            session.rpcResponse(rpcId,AuthRpcVo.reject("会话阶段异常,无法继续认证!"));
            return;
        }

        var playerName = dto.playerName();
        var clientVersion = dto.clientVersion();

        log.info("会话:{} 正在请求认证 玩家名称: {} 客户端版本: {}", session.getId(), playerName, clientVersion);

        //查询数据库 获取玩家信息
        ArchivePlayerVo playerVo = aps.loadPlayer(playerName);
        var playerDto = new ArchivePlayerDto();

        //玩家不存在，创建新玩家
        if (playerVo == null) {

            //获取服务器默认的世界信息
            ServerWorld defaultWorld = sws.getWorld(server.getDefaultWorldName());

            //默认世界未加载或不存在 踢出玩家
            if (defaultWorld == null) {
                log.error("服务器默认世界[{}]未加载或不存在 踢出玩家: {}", server.getDefaultWorldName(), playerName);
                AuthRpcVo.reject("服务器默认世界[" + server.getDefaultWorldName() + "]未加载或不存在");
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
            aps.savePlayer(playerDto);
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
            aps.savePlayer(playerDto);
        }

        //查询最新的玩家信息
        playerVo = aps.loadPlayer(playerName);

        //认证成功 返回认证结果
        session.setStage(NetworkSession.Stage.AUTHORIZED);
        session.setArchive(playerVo);
        log.info("会话:{} 认证成功 玩家名称: {} 客户端版本: {}", session.getId(), playerName, clientVersion);
        session.rpcResponse(rpcId,AuthRpcVo.accept(session.getId()));

        //必须为AUTHORIZED阶段才能处理批数据同步
        if(!session.isStage(NetworkSession.Stage.AUTHORIZED)){
            return;
        }

        var archive = session.getArchive();

        if(archive == null){
            log.error("会话:{} 无法同步批数据,玩家归档数据不存在", session.getId());
            session.close("无法同步批数据,玩家归档数据不存在!");
            return;
        }

        log.info("会话:{} 玩家:{} 正在同步批数据", session.getId(), archive.getName());

        //发送批数据(测试用，正式上线时需要根据实际情况发送(主要是调色板、注册表、材质同步) kind=-1代表服务端批数据全部发完)
        session.sendNext(BatchDataNVo.of(-1, "BatchData".getBytes()));

        //等待客户端确认批数据(客户端本地完成批数据配置后会发送BatchDataFinishNDto数据包)
        session.waitFor(BatchDataFinishNDto.class, 5, TimeUnit.MINUTES);

        //处理客户端进程切换
        psToWorld(session, archive.getWorldName());
    }

    /**
     * 开始进程切换
     * @param session 客户端网络会话
     * @param targetWorld 目标世界名称
     */
    public void psToWorld(NetworkSession session, String targetWorld){

        //必须为AUTHORIZED阶段才能处理通知客户端需要进行进程切换
        if(!session.isStage(NetworkSession.Stage.AUTHORIZED)){
            session.close("无法开始进程切换,当前会话阶段不为期望的阶段!");
            return;
        }
        session.setStage(NetworkSession.Stage.PROCESSED);

        var archive = session.getArchive();

        if(archive == null){
            log.error("会话:{} 无法进行进程切换,玩家归档数据不存在", session.getId());
            session.close("无法进行进程切换,玩家归档数据不存在!");
            return;
        }

        //查询目标世界
        ServerWorld world = sws.getWorld(targetWorld);

        if(world == null){
            log.error("会话:{} 玩家:{} 无法进行进程切换,目标世界不存在", session.getId(), archive.getName());
            session.close("无法进行进程切换,目标世界不存在!");
            return;
        }

        //获取玩家落地位置
        var groundPos = PrecisionPos.of(archive.getPosX(), archive.getPosY(), archive.getPosZ());
        var groundChunkPos = groundPos.toChunkPos(world.getTemplate().getChunkSizeX(), world.getTemplate().getChunkSizeZ());

        //在玩家落地位置的3X3签发永久租约
        ChunkViewPort vp = ChunkViewPort.of(groundChunkPos,1);
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
                return;
            }

        }

        var aps = world.getTemplate().getActionPerSecond();
        var totalActions = world.getSwts().getTotalActions();
        var startDateTime = world.getTemplate().getStartDateTime();

        //区块加载完成 通知客户端可以开始进程切换
        session.sendNext(PsNVo.of(world.getName(), aps, totalActions, startDateTime));
        log.info("会话:{} 玩家:{} 开始进程切换", session.getId(), archive.getName());
        session.setStage(NetworkSession.Stage.PROCESS_SWITCHING);

        //等待客户端接受进程切换(收到PsAllowNDto数据包)
        session.waitFor(PsAllowNDto.class, 5, TimeUnit.MINUTES);

        //必须为PROCESS_SWITCHING阶段才能处理客户端确认进程切换
        if(!session.isStage(NetworkSession.Stage.PROCESS_SWITCHING)){
            session.close("无法发送进程切换数据,当前会话阶段不为期望的阶段!");
            return;
        }

        var groundChunk = world.getFscs().loadOrGenerate(groundChunkPos);

        try{

            //发送进程切换数据(世界区块数据、玩家数据、周围其他实体数据)
            var chunkData = groundChunk.get(16, TimeUnit.SECONDS);
            var serializedData = FlexChunkSerializer.serialize(chunkData.getBlockData());
            session.sendNext(PsChunkNVo.of(groundChunkPos.getX(), groundChunkPos.getZ(), serializedData));

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
            session.sendNext(PsPlayerNVo.of(uuid, name, health, hungry, posX, posY, posZ, yaw, pitch));
            log.info("会话:{} 玩家:{} 发送进程切换数据", session.getId(), archive.getName());

            //等待客户端进程切换完成
            session.waitFor(PsFinishNDto.class,5,TimeUnit.MINUTES);

            //投入玩家到世界
            session.joinWorld(world, archive);
            session.setStage(NetworkSession.Stage.IN_WORLD);

            //通知玩家已经被投入世界
            session.sendNext(new PsJoinWorldNVo());
            log.info("会话:{} 玩家:{} 完成进程切换 已加入世界:{}", session.getId(), archive.getName(), world.getName());

        }catch (Exception ex){
            log.error("会话:{} 无法发送进程切换数据,玩家落地位置区块加载失败", session.getId());
            session.close("无法发送进程切换数据,玩家落地位置区块加载失败!");
        }

    }


}
