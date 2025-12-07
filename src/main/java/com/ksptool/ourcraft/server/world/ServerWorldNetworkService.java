package com.ksptool.ourcraft.server.world;

import com.ksptool.ourcraft.server.entity.ServerPlayer;
import com.ksptool.ourcraft.server.network.NetworkSession;
import com.ksptool.ourcraft.server.world.chunk.FlexServerChunkService;
import com.ksptool.ourcraft.sharedcore.network.nvo.HuChunkNVo;
import com.ksptool.ourcraft.sharedcore.utils.FlexChunkSerializer;
import com.ksptool.ourcraft.sharedcore.network.nvo.PsChunkNVo;
import com.ksptool.ourcraft.sharedcore.utils.position.ChunkPos;
import com.ksptool.ourcraft.sharedcore.utils.viewport.ChunkViewPort;
import com.ksptool.ourcraft.sharedcore.world.SharedWorld;
import com.ksptool.ourcraft.sharedcore.world.WorldService;
import lombok.extern.slf4j.Slf4j;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ServerWorldNetworkService extends WorldService {

    private final ServerWorld world;

    private final FlexServerChunkService fscs;

    private final SimpleEntityService ses;

    // 维护每个玩家已发送的区块集合 SessionId -> Set<ChunkPos>
    private final Map<Long, Set<ChunkPos>> sentChunks = new ConcurrentHashMap<>();

    public ServerWorldNetworkService(ServerWorld world) {
        this.world = world;
        this.fscs = world.getFscs();
        this.ses = world.getSes();
    }

    @Override
    public void action(double delta, SharedWorld world) {

        // 遍历世界中的所有实体，筛选出ServerPlayer
        var entities = ses.getEntities();

        for (var entity : entities) {
            if (!(entity instanceof ServerPlayer player)) {
                continue;
            }

            // 获取玩家的网络会话
            var session = player.getSession();
            if (session == null) {
                continue;
            }

            // 只处理已经加入世界的玩家
            long sessionId = session.getId();
            if (sessionId == 0) {
                continue;
            }

            // 检查会话状态
            if (session.getStage() != NetworkSession.Stage.IN_WORLD) {
                continue;
            }

            // 获取玩家当前所在区块位置
            var currentChunkPos = player.getCurrentChunkPos();
            if (currentChunkPos == null) {
                continue;
            }

            // 获取玩家视距
            int viewDistance = player.getViewDistance();

            // 获取或创建该玩家的已发送区块集合
            var playerSentChunks = sentChunks.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet());

            // 使用ChunkViewPort计算视口内的所有区块
            var viewPort = ChunkViewPort.of(currentChunkPos, viewDistance);
            var chunksInView = viewPort.getChunkPosSet();

            for (var targetChunkPos : chunksInView) {

                // 如果已经发送过，跳过
                if (playerSentChunks.contains(targetChunkPos)) {
                    continue;
                }

                // 检查该区块是否已经READY
                if (!fscs.isChunkReady(targetChunkPos)) {
                    // 若区块未准备好，主动触发加载（幂等操作，确保区块进入加载流程）
                    fscs.loadOrGenerate(targetChunkPos);
                    continue;
                }

                // 标记为已发送 (防止重复提交任务)
                playerSentChunks.add(targetChunkPos);

                // 提交到网络线程池进行异步发送
                this.world.getServer().getNETWORK_THREAD_POOL().submit(() -> {
                    try {
                        // 再次检查会话状态
                        if (!session.isActive() || session.getStage() != NetworkSession.Stage.IN_WORLD) {
                            return;
                        }

                        // 检查区块是否依然Ready (可能在排队时被卸载)
                        if (!fscs.isChunkReady(targetChunkPos)) {
                            playerSentChunks.remove(targetChunkPos);
                            return;
                        }

                        var chunkFuture = fscs.loadOrGenerate(targetChunkPos);
                        var chunk = chunkFuture.get(1, TimeUnit.SECONDS);

                        if (chunk == null) {
                            playerSentChunks.remove(targetChunkPos);
                            return;
                        }

                        // 序列化区块数据
                        var serializedData = FlexChunkSerializer.serialize(chunk.getFlexChunkData());

                        // 发送区块数据包
                        var chunkPacket = HuChunkNVo.of(
                                targetChunkPos.getX(),
                                targetChunkPos.getZ(),
                                serializedData);

                        session.sendNext(chunkPacket);

                        log.debug("发送区块 [{}, {}] 给玩家 SessionId: {}",
                                targetChunkPos.getX(), targetChunkPos.getZ(), sessionId);

                    } catch (Exception e) {
                        // 区块加载失败或超时，移除已发送标记以便重试
                        playerSentChunks.remove(targetChunkPos);
                        log.warn("加载或发送区块失败: [{}, {}], 原因: {}",
                                targetChunkPos.getX(), targetChunkPos.getZ(), e.getMessage());
                    }
                });
            }
        }
    }
}
