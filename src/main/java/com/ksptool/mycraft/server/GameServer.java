package com.ksptool.mycraft.server;

import com.ksptool.mycraft.server.entity.ServerPlayer;
import com.ksptool.mycraft.server.world.ServerWorld;
import com.ksptool.mycraft.sharedcore.events.ChunkDataEvent;
import com.ksptool.mycraft.sharedcore.events.ChunkUnloadEvent;
import com.ksptool.mycraft.sharedcore.events.EventQueue;
import com.ksptool.mycraft.sharedcore.events.GameEvent;
import com.ksptool.mycraft.sharedcore.events.PlayerInputEvent;
import com.ksptool.mycraft.sharedcore.events.PlayerHotbarSwitchEvent;
import com.ksptool.mycraft.sharedcore.events.PlayerActionEvent;
import com.ksptool.mycraft.sharedcore.events.PlayerAction;
import com.ksptool.mycraft.sharedcore.events.PlayerCameraInputEvent;
import com.ksptool.mycraft.sharedcore.events.PlayerUpdateEvent;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3f;

/**
 * 游戏服务端，负责游戏逻辑更新（Tick-based loop）
 */
@Slf4j
@Getter
public class GameServer {
    private static final int INITIAL_RENDER_DISTANCE = 8;
    
    private ServerWorld world;
    private ServerPlayer player;
    private boolean running;
    private Thread serverThread;
    private int playerLastChunkX = Integer.MIN_VALUE;
    private int playerLastChunkZ = Integer.MIN_VALUE;
    private boolean initialSyncPerformed = false;
    
    public GameServer() {
        this.running = false;
    }
    
    public void init(ServerWorld world, ServerPlayer player) {
        this.world = world;
        this.player = player;
        this.initialSyncPerformed = false;
    }
    
    public void start() {
        if (running) {
            return;
        }
        
        running = true;
        serverThread = new Thread(this::run, "GameServer");
        serverThread.start();
    }
    
    private void performInitialSync() {
        if (world == null || player == null) {
            return;
        }
        log.info("执行初始视口同步...");
        int playerChunkX = (int) Math.floor(player.getPosition().x / com.ksptool.mycraft.server.world.ServerChunk.CHUNK_SIZE);
        int playerChunkZ = (int) Math.floor(player.getPosition().z / com.ksptool.mycraft.server.world.ServerChunk.CHUNK_SIZE);
        
        for (int x = playerChunkX - INITIAL_RENDER_DISTANCE; x <= playerChunkX + INITIAL_RENDER_DISTANCE; x++) {
            for (int z = playerChunkZ - INITIAL_RENDER_DISTANCE; z <= playerChunkZ + INITIAL_RENDER_DISTANCE; z++) {
                com.ksptool.mycraft.server.world.ServerChunk chunk = world.getChunk(x, z);
                if (chunk == null) {
                    world.generateChunkSynchronously(x, z);
                    chunk = world.getChunk(x, z);
                }
                if (chunk != null) {
                    int[][][] blockStates = new int[com.ksptool.mycraft.server.world.ServerChunk.CHUNK_SIZE][com.ksptool.mycraft.server.world.ServerChunk.CHUNK_HEIGHT][com.ksptool.mycraft.server.world.ServerChunk.CHUNK_SIZE];
                    for (int localX = 0; localX < com.ksptool.mycraft.server.world.ServerChunk.CHUNK_SIZE; localX++) {
                        for (int y = 0; y < com.ksptool.mycraft.server.world.ServerChunk.CHUNK_HEIGHT; y++) {
                            for (int localZ = 0; localZ < com.ksptool.mycraft.server.world.ServerChunk.CHUNK_SIZE; localZ++) {
                                blockStates[localX][y][localZ] = chunk.getBlockState(localX, y, localZ);
                            }
                        }
                    }
                    EventQueue eventQueue = EventQueue.getInstance();
                    eventQueue.offerS2C(new ChunkDataEvent(x, z, blockStates));
                }
            }
        }
        
        playerLastChunkX = playerChunkX;
        playerLastChunkZ = playerChunkZ;
    }
    
    private void processEvents() {
        java.util.List<GameEvent> events = EventQueue.getInstance().pollAllC2S();
        
        if (!events.isEmpty()) {
            log.debug("GameServer: 处理{}个事件", events.size());
        }
        
        for (GameEvent event : events) {
            if (event instanceof PlayerInputEvent) {
                handlePlayerInput((PlayerInputEvent) event);
            } else if (event instanceof PlayerHotbarSwitchEvent) {
                handlePlayerHotbarSwitch((PlayerHotbarSwitchEvent) event);
            } else if (event instanceof PlayerActionEvent) {
                handlePlayerAction((PlayerActionEvent) event);
            } else if (event instanceof PlayerCameraInputEvent) {
                handlePlayerCameraInput((PlayerCameraInputEvent) event);
            }
        }
    }
    
    private void handlePlayerInput(PlayerInputEvent event) {
        if (player == null) {
            log.warn("GameServer: 收到PlayerInputEvent但player为null");
            return;
        }
        
        player.applyInput(event);
    }
    
    private void handlePlayerHotbarSwitch(PlayerHotbarSwitchEvent event) {
        if (player == null) {
            return;
        }
        
        player.getInventory().scrollSelection(event.getSlotDelta());
        player.markDirty(true);
    }
    
    private void handlePlayerAction(PlayerActionEvent event) {
        if (player == null) {
            return;
        }
        
        if (event.getAction() == PlayerAction.ATTACK) {
            player.handleBlockBreak();
        } else if (event.getAction() == PlayerAction.USE) {
            player.handleBlockPlace();
        }
    }
    
    private void handlePlayerCameraInput(PlayerCameraInputEvent event) {
        if (player == null) {
            return;
        }
        
        player.updateCameraOrientation(event.getDeltaYaw(), event.getDeltaPitch());
    }
    
    public void stop() {
        running = false;
        if (serverThread != null) {
            try {
                serverThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    private void run() {
        log.info("GameServer主循环已启动");
        double lastTime = System.nanoTime();
        
        while (running) {
            double now = System.nanoTime();
            double deltaSeconds = (now - lastTime) / 1_000_000_000.0;
            lastTime = now;
            
            if (deltaSeconds > 0.1) {
                deltaSeconds = 0.1;
            }
            
            if (world == null || player == null) {
                log.warn("GameServer: world或player为null，跳过本次更新");
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                continue;
            }
            
            processEvents();
            
            if (!initialSyncPerformed) {
                performInitialSync();
                initialSyncPerformed = true;
                log.info("初始视口同步已完成");
            }
            
            Vector3f playerPosition = player.getPosition();
            world.update((float) deltaSeconds, playerPosition, () -> {
                if (player != null) {
                    float tickDelta = 1.0f / world.getTemplate().getTicksPerSecond();
                    player.update(tickDelta);
                }
            });
            
            updateDynamicViewport();
            
            sendPlayerUpdate();
            
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        log.info("GameServer主循环已停止");
    }
    
    private void sendPlayerUpdate() {
        if (player == null) {
            return;
        }
        
        EventQueue eventQueue = EventQueue.getInstance();
        eventQueue.offerS2C(new PlayerUpdateEvent(
            player.getPosition(),
            player.getPreviousPosition(),
            player.getYaw(),
            player.getPitch(),
            player.getPreviousYaw(),
            player.getPreviousPitch(),
            player.getInventory().getSelectedSlot()
        ));
    }
    
    private void updateDynamicViewport() {
        if (world == null || player == null) {
            return;
        }
        
        int playerChunkX = (int) Math.floor(player.getPosition().x / com.ksptool.mycraft.server.world.ServerChunk.CHUNK_SIZE);
        int playerChunkZ = (int) Math.floor(player.getPosition().z / com.ksptool.mycraft.server.world.ServerChunk.CHUNK_SIZE);
        
        if (playerLastChunkX == Integer.MIN_VALUE || playerLastChunkZ == Integer.MIN_VALUE) {
            playerLastChunkX = playerChunkX;
            playerLastChunkZ = playerChunkZ;
            return;
        }
        
        if (playerChunkX == playerLastChunkX && playerChunkZ == playerLastChunkZ) {
            return;
        }
        
        java.util.Set<java.util.AbstractMap.SimpleEntry<Integer, Integer>> oldViewport = new java.util.HashSet<>();
        java.util.Set<java.util.AbstractMap.SimpleEntry<Integer, Integer>> newViewport = new java.util.HashSet<>();
        
        for (int x = playerLastChunkX - INITIAL_RENDER_DISTANCE; x <= playerLastChunkX + INITIAL_RENDER_DISTANCE; x++) {
            for (int z = playerLastChunkZ - INITIAL_RENDER_DISTANCE; z <= playerLastChunkZ + INITIAL_RENDER_DISTANCE; z++) {
                oldViewport.add(new java.util.AbstractMap.SimpleEntry<>(x, z));
            }
        }
        
        for (int x = playerChunkX - INITIAL_RENDER_DISTANCE; x <= playerChunkX + INITIAL_RENDER_DISTANCE; x++) {
            for (int z = playerChunkZ - INITIAL_RENDER_DISTANCE; z <= playerChunkZ + INITIAL_RENDER_DISTANCE; z++) {
                newViewport.add(new java.util.AbstractMap.SimpleEntry<>(x, z));
            }
        }
        
        EventQueue eventQueue = EventQueue.getInstance();
        
        for (java.util.AbstractMap.SimpleEntry<Integer, Integer> chunkPos : newViewport) {
            if (!oldViewport.contains(chunkPos)) {
                int x = chunkPos.getKey();
                int z = chunkPos.getValue();
                com.ksptool.mycraft.server.world.ServerChunk chunk = world.getChunk(x, z);
                if (chunk != null) {
                    int[][][] blockStates = new int[com.ksptool.mycraft.server.world.ServerChunk.CHUNK_SIZE][com.ksptool.mycraft.server.world.ServerChunk.CHUNK_HEIGHT][com.ksptool.mycraft.server.world.ServerChunk.CHUNK_SIZE];
                    for (int localX = 0; localX < com.ksptool.mycraft.server.world.ServerChunk.CHUNK_SIZE; localX++) {
                        for (int y = 0; y < com.ksptool.mycraft.server.world.ServerChunk.CHUNK_HEIGHT; y++) {
                            for (int localZ = 0; localZ < com.ksptool.mycraft.server.world.ServerChunk.CHUNK_SIZE; localZ++) {
                                blockStates[localX][y][localZ] = chunk.getBlockState(localX, y, localZ);
                            }
                        }
                    }
                    eventQueue.offerS2C(new ChunkDataEvent(x, z, blockStates));
                }
            }
        }
        
        for (java.util.AbstractMap.SimpleEntry<Integer, Integer> chunkPos : oldViewport) {
            if (!newViewport.contains(chunkPos)) {
                int x = chunkPos.getKey();
                int z = chunkPos.getValue();
                eventQueue.offerS2C(new ChunkUnloadEvent(x, z));
            }
        }
        
        playerLastChunkX = playerChunkX;
        playerLastChunkZ = playerChunkZ;
    }
    
    public void cleanup() {
        stop();
        if (world != null) {
            world.saveAllDirtyData();
            world.cleanup();
        }
    }
}

