package com.ksptool.ourcraft.server.manager;

import com.ksptool.ourcraft.server.OurCraftServerInstance;
import com.ksptool.ourcraft.server.entity.ServerPlayer;
import com.ksptool.ourcraft.server.network.ClientConnectionHandler;
import com.ksptool.ourcraft.server.world.ServerChunk;
import com.ksptool.ourcraft.server.world.ServerWorld;
import com.ksptool.ourcraft.sharedcore.events.*;
import com.ksptool.ourcraft.sharedcore.network.packets.ServerSyncBlockUpdateNVo;
import com.ksptool.ourcraft.sharedcore.network.packets.ServerSyncChunkDataNVo;
import com.ksptool.ourcraft.sharedcore.network.packets.ServerSyncEntityPositionAndRotationNVo;
import com.ksptool.ourcraft.sharedcore.network.packets.ServerSyncPlayerStateNVo;
import com.ksptool.ourcraft.sharedcore.network.packets.ServerSyncUnloadChunkNVo;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3f;

import java.util.AbstractMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 服务端世界执行单元
 * 负责执行单个服务端世界的逻辑（Tick循环、物理更新、事件处理、网络同步）
 */
@Slf4j
public class ServerWorldExecutionUnit implements Runnable {

    private final String worldName;
    
    private final ServerWorld serverWorld;

    private final OurCraftServerInstance serverInstance; // 需要引用以访问连接的客户端

    private static final int INITIAL_RENDER_DISTANCE = 8; // 也可以从配置读取

    @Getter
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    public ServerWorldExecutionUnit(ServerWorld serverWorld, OurCraftServerInstance serverInstance) {
        this.worldName = serverWorld.getWorldName();
        this.serverWorld = serverWorld;
        this.serverInstance = serverInstance;
    }

    @Override
    public void run() {
        
        log.info("世界执行单元已启动: {}", worldName);
        isRunning.set(true);

        if (serverWorld.getTemplate() == null) {
            log.error("无法启动世界 {}: template 为 null", worldName);
            isRunning.set(false);
            return;
        }

        final double tickRate = serverWorld.getTemplate().getTicksPerSecond();
        final double tickTime = 1.0 / tickRate;

        double lastTime = System.nanoTime() / 1_000_000_000.0;
        double accumulator = 0.0;

        while (isRunning.get()) {
            double now = System.nanoTime() / 1_000_000_000.0;
            double deltaSeconds = now - lastTime;
            lastTime = now;

            accumulator += deltaSeconds;

            boolean ticked = false;
            // 追赶逻辑 (Catch-up)
            while (accumulator >= tickTime) {
                tick((float) tickTime);
                accumulator -= tickTime;
                ticked = true;
            }

            // 如果执行了逻辑更新，则发送网络同步
            if (ticked) {
                sendNetworkUpdates();
            }

            // 防止 CPU 空转
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                isRunning.set(false);
            }
        }

        log.info("世界执行单元已停止: {}", worldName);
    }

    public void stop() {
        isRunning.set(false);
    }

    /**
     * 执行一次逻辑 Tick
     */
    private void tick(float tickDelta) {
        // 1. 处理事件 (输入等)
        processEvents();

        // 2. 计算中心位置 (用于区块加载优化等)
        Vector3f centerPosition = new Vector3f(0, 64, 0);
        // TODO: 如果支持多世界，这里应该只获取在这个世界里的玩家位置
        List<ClientConnectionHandler> clients = serverInstance.getConnectedClients();
        if (!clients.isEmpty()) {
            for (ClientConnectionHandler handler : clients) {
                ServerPlayer player = handler.getPlayer();
                if (player != null && player.getWorld() == serverWorld) {
                    centerPosition.set(player.getPosition());
                    break; // 暂时以第一个玩家为中心，或者计算所有玩家的中心
                }
            }
        }

        // 3. 更新世界物理和实体
        serverWorld.update(tickDelta, centerPosition, () -> {
            List<com.ksptool.ourcraft.server.entity.ServerEntity> entities = new java.util.ArrayList<>(serverWorld.getEntities());
            for (com.ksptool.ourcraft.server.entity.ServerEntity entity : entities) {
                if (entity instanceof ServerPlayer) {
                    ServerPlayer player = (ServerPlayer) entity;
                    // 查找Handler检查是否初始化
                    ClientConnectionHandler handler = findHandlerForPlayer(player);
                    if (handler != null && !handler.isPlayerInitialized()) {
                        continue;
                    }
                }
                entity.update(tickDelta);
            }
        });
    }

    /**
     * 处理网络同步
     */
    private void sendNetworkUpdates() {
        // 1. 发送方块更新
        processBlockUpdates();

        // 2. 发送区块加载/卸载信息 (视口更新)
        updateDynamicViewport();

        // 3. 发送玩家和实体的位置信息
        sendPlayerUpdate();
    }

    // --- 以下是从 OurCraftServerInstance 迁移过来的逻辑，并针对单世界进行了适配 ---

    private void processEvents() {
        // 注意：EventQueue目前是全局的。在多世界环境下，事件应该包含WorldID，或者EventQueue应该按世界分发。
        // 这里假设 EventQueue.pollAllC2S 取出的事件如果是玩家输入，我们需要判断玩家是否在这个世界。
        List<GameEvent> events = EventQueue.getInstance().pollAllC2S();

        // 简单的 SP/MP 混合处理逻辑
        if (!events.isEmpty()) {
             // 遍历本世界所有玩家实体处理事件
             // 优化：应该建立 PlayerID -> PlayerEntity 的映射快速查找
            List<com.ksptool.ourcraft.server.entity.ServerEntity> entities = serverWorld.getEntities();
            
            for (com.ksptool.ourcraft.server.entity.ServerEntity entity : entities) {
                if (entity instanceof ServerPlayer) {
                    ServerPlayer player = (ServerPlayer) entity;
                    // 这里有个简化的假设：目前所有取出的事件都尝试应用给这个玩家
                    // 实际上应该根据事件中的 sourceEntityId 或 playerId 来分发
                    // 暂时保留原逻辑的结构，但需注意多玩家/多世界时的事件路由问题
                    
                    for (GameEvent event : events) {
                        // 只有当事件属于该玩家时才应用 (需要事件系统支持源标识，这里暂时全量应用)
                        // 在单人演示中通常只有一个玩家，所以暂无问题
                        applyEventToPlayer(player, event);
                    }
                }
            }
        }
    }

    private void applyEventToPlayer(ServerPlayer player, GameEvent event) {
        if (event instanceof PlayerInputEvent) {
            player.applyInput((PlayerInputEvent) event);
        } else if (event instanceof PlayerHotbarSwitchEvent) {
            player.getInventory().scrollSelection(((PlayerHotbarSwitchEvent) event).getSlotDelta());
            player.markDirty(true);
        } else if (event instanceof PlayerActionEvent) {
            PlayerActionEvent actionEvent = (PlayerActionEvent) event;
            if (actionEvent.getAction() == PlayerAction.ATTACK) {
                player.handleBlockBreak();
            } else if (actionEvent.getAction() == PlayerAction.USE) {
                player.handleBlockPlace();
            }
        } else if (event instanceof PlayerCameraInputEvent) {
            PlayerCameraInputEvent cameraEvent = (PlayerCameraInputEvent) event;
            player.updateCameraOrientation(cameraEvent.getDeltaYaw(), cameraEvent.getDeltaPitch());
        }
    }

    private void processBlockUpdates() {
        // 获取本世界的 S2C 事件
        // TODO: EventQueue pollAllS2C 也是全局的，需要过滤属于本世界的事件
        List<GameEvent> s2cEvents = EventQueue.getInstance().pollAllS2C();
        
        for (GameEvent event : s2cEvents) {
            if (event instanceof BlockUpdateEvent) {
                BlockUpdateEvent blockEvent = (BlockUpdateEvent) event;
                // 简单的过滤：检查坐标是否在加载的区块内等，或者假设ServerWorld只会发出它自己的事件
                // 但 EventQueue 是静态单例，多个世界会混杂。
                // 临时方案：假定当前只有一个活跃世界，或者需要给 BlockUpdateEvent 加 World 字段
                
                ServerSyncBlockUpdateNVo packet = new ServerSyncBlockUpdateNVo(
                        blockEvent.getX(), blockEvent.getY(), blockEvent.getZ(), blockEvent.getNewStateId()
                );

                broadcastPacketToWorldPlayers(packet);
            }
        }
    }

    private void sendPlayerUpdate() {
        List<ClientConnectionHandler> clients = serverInstance.getConnectedClients();
        for (ClientConnectionHandler handler : clients) {
            if (!handler.isConnected()) continue;
            
            ServerPlayer player = handler.getPlayer();
            // 过滤：只处理在这个世界中的玩家
            if (player == null || player.getWorld() != serverWorld) continue; 
            if (!handler.isPlayerInitialized()) continue;

            // 发送该玩家的位置和状态
            handler.sendPacket(new ServerSyncEntityPositionAndRotationNVo(
                    0, player.getPosition().x, player.getPosition().y, player.getPosition().z,
                    player.getYaw(), player.getPitch()
            ));

            handler.sendPacket(new ServerSyncPlayerStateNVo(
                    player.getHealth(), 20, 0, 0.0f
            ));
        }
        
        broadcastEntityPositions();
    }

    private void broadcastEntityPositions() {
        // 广播本世界所有实体位置
        List<com.ksptool.ourcraft.server.entity.ServerEntity> entities = new java.util.ArrayList<>(serverWorld.getEntities());
        
        for (com.ksptool.ourcraft.server.entity.ServerEntity entity : entities) {
            if (entity instanceof ServerPlayer) {
                ServerPlayer player = (ServerPlayer) entity;
                int entityId = getEntityIdForPlayer(player);
                if (entityId == 0) continue;

                ServerSyncEntityPositionAndRotationNVo packet = new ServerSyncEntityPositionAndRotationNVo(
                        entityId, player.getPosition().x, player.getPosition().y, player.getPosition().z,
                        player.getYaw(), player.getPitch()
                );

                // 广播给本世界除自己外的其他人
                List<ClientConnectionHandler> clients = serverInstance.getConnectedClients();
                for (ClientConnectionHandler handler : clients) {
                    if (!handler.isConnected()) continue;
                    ServerPlayer targetP = handler.getPlayer();
                    if (targetP != null && targetP.getWorld() == serverWorld && targetP != player) {
                        handler.sendPacket(packet);
                    }
                }
            }
        }
    }

    private void updateDynamicViewport() {
        List<ClientConnectionHandler> clients = serverInstance.getConnectedClients();
        for (ClientConnectionHandler handler : clients) {
            if (!handler.isConnected()) continue;
            ServerPlayer player = handler.getPlayer();
            if (player == null || player.getWorld() != serverWorld) continue;

            updatePlayerViewport(handler, player);
        }
    }

    private void updatePlayerViewport(ClientConnectionHandler handler, ServerPlayer player) {
        int playerChunkX = (int) Math.floor(player.getPosition().x / ServerChunk.CHUNK_SIZE);
        int playerChunkZ = (int) Math.floor(player.getPosition().z / ServerChunk.CHUNK_SIZE);

        Integer lastChunkX = handler.getLastChunkX();
        Integer lastChunkZ = handler.getLastChunkZ();

        if (lastChunkX == null || lastChunkZ == null) {
            handler.setLastChunkX(playerChunkX);
            handler.setLastChunkZ(playerChunkZ);
            return;
        }

        if (playerChunkX == lastChunkX && playerChunkZ == lastChunkZ) return;

        Set<AbstractMap.SimpleEntry<Integer, Integer>> oldViewport = new HashSet<>();
        Set<AbstractMap.SimpleEntry<Integer, Integer>> newViewport = new HashSet<>();

        for (int x = lastChunkX - INITIAL_RENDER_DISTANCE; x <= lastChunkX + INITIAL_RENDER_DISTANCE; x++) {
            for (int z = lastChunkZ - INITIAL_RENDER_DISTANCE; z <= lastChunkZ + INITIAL_RENDER_DISTANCE; z++) {
                oldViewport.add(new AbstractMap.SimpleEntry<>(x, z));
            }
        }
        for (int x = playerChunkX - INITIAL_RENDER_DISTANCE; x <= playerChunkX + INITIAL_RENDER_DISTANCE; x++) {
            for (int z = playerChunkZ - INITIAL_RENDER_DISTANCE; z <= playerChunkZ + INITIAL_RENDER_DISTANCE; z++) {
                newViewport.add(new AbstractMap.SimpleEntry<>(x, z));
            }
        }

        // Load new
        for (AbstractMap.SimpleEntry<Integer, Integer> chunkPos : newViewport) {
            if (!oldViewport.contains(chunkPos)) {
                sendChunkToClient(handler, chunkPos.getKey(), chunkPos.getValue());
            }
        }
        // Unload old
        for (AbstractMap.SimpleEntry<Integer, Integer> chunkPos : oldViewport) {
            if (!newViewport.contains(chunkPos)) {
                handler.sendPacket(new ServerSyncUnloadChunkNVo(chunkPos.getKey(), 0, chunkPos.getValue()));
            }
        }
        handler.setLastChunkX(playerChunkX);
        handler.setLastChunkZ(playerChunkZ);
    }

    private void sendChunkToClient(ClientConnectionHandler handler, int x, int z) {
        ServerChunk chunk = serverWorld.getChunk(x, z);
        if (chunk == null) {
            serverWorld.generateChunkSynchronously(x, z);
            chunk = serverWorld.getChunk(x, z);
        }
        if (chunk != null) {
            // 序列化逻辑提取 (简化版，实际可复用原Instance中的代码)
            int[][][] blockStates = new int[ServerChunk.CHUNK_SIZE][ServerChunk.CHUNK_HEIGHT][ServerChunk.CHUNK_SIZE];
            for (int lx = 0; lx < ServerChunk.CHUNK_SIZE; lx++) {
                for (int y = 0; y < ServerChunk.CHUNK_HEIGHT; y++) {
                    for (int lz = 0; lz < ServerChunk.CHUNK_SIZE; lz++) {
                        blockStates[lx][y][lz] = chunk.getBlockState(lx, y, lz);
                    }
                }
            }
            byte[] data = serializeChunkData(blockStates);
            handler.sendPacket(new ServerSyncChunkDataNVo(x, 0, z, data));
        }
    }
    
    private byte[] serializeChunkData(int[][][] blockStates) {
        int size = ServerChunk.CHUNK_SIZE * ServerChunk.CHUNK_HEIGHT * ServerChunk.CHUNK_SIZE;
        byte[] data = new byte[size * 4];
        int index = 0;
        for (int x = 0; x < ServerChunk.CHUNK_SIZE; x++) {
            for (int y = 0; y < ServerChunk.CHUNK_HEIGHT; y++) {
                for (int z = 0; z < ServerChunk.CHUNK_SIZE; z++) {
                    int stateId = blockStates[x][y][z];
                    data[index++] = (byte) (stateId & 0xFF);
                    data[index++] = (byte) ((stateId >> 8) & 0xFF);
                    data[index++] = (byte) ((stateId >> 16) & 0xFF);
                    data[index++] = (byte) ((stateId >> 24) & 0xFF);
                }
            }
        }
        return data;
    }

    private void broadcastPacketToWorldPlayers(Object packet) {
        List<ClientConnectionHandler> clients = serverInstance.getConnectedClients();
        for (ClientConnectionHandler handler : clients) {
            if (handler.isConnected() && handler.getPlayer() != null && handler.getPlayer().getWorld() == serverWorld) {
                handler.sendPacket(packet);
            }
        }
    }

    private ClientConnectionHandler findHandlerForPlayer(ServerPlayer player) {
        return serverInstance.getConnectedClients().stream()
                .filter(h -> h.getPlayer() == player)
                .findFirst().orElse(null);
    }

    private int getEntityIdForPlayer(ServerPlayer player) {
        // 这里需要一个更可靠的 ID 获取方式，目前沿用 session ID 的逻辑
        // 实际项目中建议 ServerPlayer 自身携带 EntityID
        return serverInstance.getSessionIdToHandler().entrySet().stream()
                .filter(e -> e.getValue().getPlayer() == player)
                .map(java.util.Map.Entry::getKey)
                .findFirst().orElse(0);
    }

    /**
     * 获取服务器世界
     * @return 服务器世界
     */
    public ServerWorld getServerWorld() {
        return serverWorld;
    }
}