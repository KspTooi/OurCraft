package com.ksptool.ourcraft.client;

import com.ksptool.ourcraft.server.world.gen.layers.BaseDensityLayer;
import com.ksptool.ourcraft.server.world.gen.layers.FeatureLayer;
import com.ksptool.ourcraft.server.world.gen.layers.SurfaceLayer;
import com.ksptool.ourcraft.server.world.gen.layers.WaterLayer;
import com.ksptool.ourcraft.sharedcore.enums.BlockEnums;
import com.ksptool.ourcraft.sharedcore.enums.EngineDefault;
import com.ksptool.ourcraft.sharedcore.enums.GameState;
import com.ksptool.ourcraft.client.world.ClientWorld;
import com.ksptool.ourcraft.client.entity.ClientPlayer;
import com.ksptool.ourcraft.client.rendering.Renderer;
import com.ksptool.ourcraft.client.rendering.GuiRenderer;
import com.ksptool.ourcraft.client.rendering.WorldRenderer;
import com.ksptool.ourcraft.client.gui.MainMenu;
import com.ksptool.ourcraft.client.gui.SingleplayerMenu;
import com.ksptool.ourcraft.client.gui.CreateWorldMenu;
import com.ksptool.ourcraft.client.gui.UiConstants;
import com.ksptool.ourcraft.client.network.ClientNetworkService;
import com.ksptool.ourcraft.client.network.ClientNetworkSession;
import com.ksptool.ourcraft.sharedcore.enums.WorldTemplateEnums;
import com.ksptool.ourcraft.sharedcore.events.*;
import com.ksptool.ourcraft.sharedcore.network.ndto.PlayerInputNDto;
import com.ksptool.ourcraft.sharedcore.utils.SimpleEventQueue;
import com.ksptool.ourcraft.sharedcore.network.packets.*;
import com.ksptool.ourcraft.sharedcore.network.nvo.PsNVo;
import com.ksptool.ourcraft.sharedcore.network.nvo.PsChunkNVo;
import com.ksptool.ourcraft.sharedcore.network.nvo.PsPlayerNVo;
import com.ksptool.ourcraft.sharedcore.network.nvo.PsJoinWorldNVo;
import com.ksptool.ourcraft.sharedcore.network.nvo.HuChunkNVo;
import com.ksptool.ourcraft.sharedcore.network.nvo.HuChunkUnloadNVo;
import com.ksptool.ourcraft.sharedcore.network.ndto.PsAllowNDto;
import com.ksptool.ourcraft.sharedcore.network.ndto.PsFinishNDto;
import com.ksptool.ourcraft.sharedcore.GlobalPalette;
import com.ksptool.ourcraft.sharedcore.Registry;
import com.ksptool.ourcraft.sharedcore.world.WorldTemplate;
import com.ksptool.ourcraft.sharedcore.world.gen.DefaultTerrainGenerator;
import com.ksptool.ourcraft.sharedcore.world.gen.SpawnPlatformGenerator;

import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3d;

/**
 * 游戏客户端，负责渲染、输入处理和事件消费
 */
@Slf4j
public class OurCraftClient {

    private Window window;
    private Input input;
    private Renderer renderer;
    private GuiRenderer guiRenderer;
    private ClientWorld clientWorld;
    private WorldRenderer worldRenderer;
    private ClientPlayer player;
    private boolean running;
    
    private GameState currentState = GameState.MAIN_MENU;
    
    private MainMenu mainMenu;
    private SingleplayerMenu singleplayerMenu;
    private CreateWorldMenu createWorldMenu;
    
    private ClientNetworkService clientNetworkService;
    
    private float timeOfDay = 0.0f;
    
    private volatile boolean playerInitialized = false;
    
    // 客户端逻辑帧计数器（用于客户端预测）
    private int clientTick = 0;
    
    // 待处理的多人游戏世界初始化任务（从网络线程提交到主线程）
    private volatile PendingMultiplayerInit pendingMultiplayerInit = null;
    
    private static class PendingMultiplayerInit {
        final double x;
        final double y;
        final double z;
        final float yaw;
        final float pitch;
        
        PendingMultiplayerInit(double x, double y, double z, float yaw, float pitch) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    public void init() {

        //注册所有引擎原版内容
        registerAllDefaultContent();

        GlobalPalette.getInstance().bake();
        
        window = new Window(1280, 720, "OurCraft " + EngineDefault.ENGINE_VERSION + "\uD83E\uDD86 内部预览版");
        window.init();
        input = new Input(window.getWindowHandle());
        input.setMouseLocked(false);

        renderer = new Renderer();
        renderer.init();
        renderer.resize(window.getWidth(), window.getHeight());
        
        guiRenderer = new GuiRenderer();
        guiRenderer.init();
        
        mainMenu = new MainMenu();
        singleplayerMenu = new SingleplayerMenu();
        createWorldMenu = new CreateWorldMenu();
        
        clientNetworkService = new ClientNetworkService();
        running = true;
    }

    public void run() {
        init();
        loop();
        cleanup();
    }

    private void loop() {
        double lastTime = System.nanoTime() / 1_000_000_000.0;
        
        // 固定时间步长配置（默认20 TPS，如果世界已加载则使用世界的配置）
        double tickRate = 20.0;
        double tickTime = 1.0 / tickRate;
        double accumulator = 0.0;

        while (running && !window.shouldClose()) {
            double now = System.nanoTime() / 1_000_000_000.0;
            double deltaSeconds = now - lastTime;
            lastTime = now;

            // 限制最大时间步长，防止极端情况
            if (deltaSeconds > 0.1) {
                deltaSeconds = 0.1;
            }

            input.update();
            
            // 如果世界已加载，使用世界的tickRate
            if (clientWorld != null && clientWorld.getTemplate() != null) {
                tickRate = clientWorld.getTemplate().getActionPerSecond();
                tickTime = 1.0 / tickRate;
            }
            
            accumulator += deltaSeconds;
            
            // 运行所有需要追赶的 tick（固定时间步长物理更新）
            while (accumulator >= tickTime) {
                tick((float) tickTime);
                accumulator -= tickTime;
            }
            
            // 处理非物理相关的更新（UI状态切换等）
            updateNonPhysics();

            render();
        }
    }

    /**
     * 固定时间步长的物理更新（tick）
     * 所有物理相关的逻辑都在这里执行，确保与服务端使用相同的时间步长
     */
    private void tick(float tickDelta) {

        if (currentState != GameState.IN_GAME) {
            return;
        }
        
        if (player == null || input == null) {
            return;
        }
        

        // 优先使用网络连接，如果未连接则使用EventQueue（兼容单人游戏）
        ClientNetworkSession session = clientNetworkService != null ? clientNetworkService.getSession() : null;
        boolean useNetwork = session != null && session.getStage() == ClientNetworkSession.Stage.IN_WORLD;
        
        // 处理鼠标输入（更新本地相机朝向），这个可以随时执行，因为它不影响玩家位置
        player.handleMouseInput(input);
        
        // 只有在玩家初始化完成后（即从服务器收到初始世界数据或单人游戏世界加载后）才进行物理预测和输入处理
        // 这样可以避免在没有世界数据时进行物理模拟，导致玩家位置与服务端不一致
        if (!playerInitialized) {
            return;
        }
        
        // 收集键盘输入
        boolean forward = input.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_W);
        boolean backward = input.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_S);
        boolean left = input.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_A);
        boolean right = input.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_D);
        boolean jump = input.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE);
        boolean shift = input.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) || 
                       input.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT);
        
        // 应用输入到客户端玩家（本地预测）
        PlayerInputEvent inputEvent = new PlayerInputEvent(forward, backward, left, right, jump);
        player.applyInput(inputEvent);
        
        // 更新玩家物理（客户端预测）- 使用固定的tickDelta
        player.update(tickDelta);
        
        // 发送输入状态到服务器（如果已连接）
        if (useNetwork && session != null) {
            clientTick++;
            session.sendNext(new PlayerInputNDto(
                clientTick,
                forward,
                backward,
                left,
                right,
                jump,
                shift,
                player.getYaw(),
                player.getPitch()
            ));
        } else {
            // 单人游戏模式，使用EventQueue
            SimpleEventQueue simpleEventQueue = SimpleEventQueue.getInstance();
            simpleEventQueue.offerC2S(inputEvent);
            
            org.joml.Vector2d mouseDelta = input.getMouseDelta();
            if (mouseDelta.x != 0 || mouseDelta.y != 0) {
                float mouseSensitivity = 0.1f;
                float deltaYaw = (float) mouseDelta.x * mouseSensitivity;
                float deltaPitch = (float) mouseDelta.y * mouseSensitivity;
                simpleEventQueue.offerC2S(new PlayerCameraInputEvent(deltaYaw, deltaPitch));
            }
        }
    }
    
    /**
     * 处理非物理相关的更新（UI状态切换、数据包处理等）
     */
    private void updateNonPhysics() {
        // 处理网络数据包（必须在主线程执行）
        processNetworkPackets();
        
        // 检查是否有待处理的多人游戏初始化任务（必须在主线程执行）
        PendingMultiplayerInit init = pendingMultiplayerInit;
        if (init != null) {
            pendingMultiplayerInit = null;
            initializeMultiplayerWorld(init.x, init.y, init.z, init.yaw, init.pitch);
        }
        
        if (currentState == GameState.MAIN_MENU) {
            updateMainMenu();
            return;
        }
        
        if (currentState == GameState.SINGLEPLAYER_MENU) {
            updateSingleplayerMenu();
            return;
        }
        
        if (currentState == GameState.CREATE_WORLD) {
            updateCreateWorldMenu();
            return;
        }
        
        if (currentState == GameState.IN_GAME) {
            updateInGame();
            return;
        }
        
        if (currentState == GameState.PAUSED) {
            updatePaused();
            return;
        }
    }
    
    /**
     * 处理网络数据包（从RpcSession队列中轮询）
     */
    private void processNetworkPackets() {
        if (clientNetworkService == null) {
            return;
        }
        
        ClientNetworkSession session = clientNetworkService.getSession();
        if (session == null) {
            return;
        }
        
        // 轮询所有积压的数据包
        Object packet;
        while ((packet = session.receiveNext()) != null) {
            handleNetworkPacket(session, packet);
        }
    }
    
    /**
     * 处理单个网络数据包
     */
    private void handleNetworkPacket(ClientNetworkSession session, Object packet) {
        if (packet == null) {
            return;
        }
        
        // 处理进程切换相关数据包
        if (packet instanceof PsNVo psNVo) {
            handleProcessSwitchRequest(session, psNVo);
            return;
        }
        
        if (packet instanceof PsChunkNVo psChunkNVo) {
            handleProcessSwitchChunk(session, psChunkNVo);
            return;
        }
        
        if (packet instanceof PsPlayerNVo psPlayerNVo) {
            handleProcessSwitchPlayer(session, psPlayerNVo);
            return;
        }
        
        if (packet instanceof PsJoinWorldNVo) {
            handleProcessSwitchComplete(session);
            return;
        }
        
        // 处理游戏运行时数据包
        if (packet instanceof HuChunkUnloadNVo huChunkUnloadNVo) {
            if (clientWorld != null) {
                clientWorld.handleChunkUnload(huChunkUnloadNVo);
            }
            return;
        }
        
        if (packet instanceof ServerSyncBlockUpdateNVo serverSyncBlockUpdateNVo) {
            if (clientWorld != null) {
                clientWorld.handleBlockUpdate(serverSyncBlockUpdateNVo);
            }
            return;
        }
        
        if (packet instanceof HuChunkNVo huChunkNVo) {
            if (clientWorld != null) {
                clientWorld.handleHotUpdateChunk(huChunkNVo);
            }
            return;
        }
        
        if (packet instanceof ServerSyncEntityPositionAndRotationNVo serverSyncEntityPositionAndRotationNVo) {
            handleEntityPositionAndRotation(serverSyncEntityPositionAndRotationNVo);
            return;
        }
        
        if (packet instanceof ServerSyncPlayerStateNVo serverSyncPlayerStateNVo) {
            handlePlayerState(serverSyncPlayerStateNVo);
            return;
        }
        
        if (packet instanceof ServerSyncWorldTimeNVo serverSyncWorldTimeNVo) {
            handleWorldTime(serverSyncWorldTimeNVo);
            return;
        }
        
        if (packet instanceof ServerDisconnectNVo serverDisconnectNVo) {
            handleDisconnect(serverDisconnectNVo);
            return;
        }
        
        if (packet instanceof ServerKeepAliveNPkg) {
            // 心跳包，可以在这里更新最后心跳时间（暂时不需要处理）
            return;
        }
        
        log.debug("收到未处理的网络数据包: {}", packet.getClass().getSimpleName());
    }
    
    /**
     * 处理进程切换请求 (PsNVo)
     */
    private void handleProcessSwitchRequest(ClientNetworkSession session, PsNVo psNVo) {
        if (session.getStage() != ClientNetworkSession.Stage.PROCESSED) {
            log.warn("收到进程切换请求，但当前阶段不正确: {}", session.getStage());
            return;
        }
        
        log.info("收到进程切换请求: 世界={}, APS={}, TotalActions={}", 
                psNVo.worldName(), psNVo.aps(), psNVo.totalActions());
        
        // 准备本地资源（创建世界、玩家等）
        // 在这里立即创建ClientWorld，确保后续收到的区块数据有地方存放
        WorldTemplate template = Registry.getInstance().getWorldTemplate(EngineDefault.DEFAULT_WORLD_TEMPLATE);
        if (template == null) {
            log.error("无法初始化多人游戏世界: 默认模板未找到");
            return;
        }
        
        ClientWorld newClientWorld = new ClientWorld(template);
        WorldRenderer newWorldRenderer = new WorldRenderer(newClientWorld);
        newWorldRenderer.init();
        
        // 暂时只设置world和renderer，player等收到PsPlayerNVo再创建并设置
        this.clientWorld = newClientWorld;
        this.worldRenderer = newWorldRenderer;
        
        // 这里先发送允许信号，实际的世界创建会在收到PsChunkNVo和PsPlayerNVo后完成
        session.setStage(ClientNetworkSession.Stage.PROCESS_SWITCHING);
        session.sendNext(new PsAllowNDto());
        log.info("已发送进程切换允许信号");
    }
    
    /**
     * 处理进程切换区块数据 (PsChunkNVo)
     */
    private void handleProcessSwitchChunk(ClientNetworkSession session, PsChunkNVo psChunkNVo) {
        if (session.getStage() != ClientNetworkSession.Stage.PROCESS_SWITCHING) {
            log.warn("收到进程切换区块数据，但当前阶段不正确: {}", session.getStage());
            return;
        }
        
        if (clientWorld == null) {
            log.warn("收到区块数据但ClientWorld为null");
            return;
        }
        
        log.info("收到进程切换区块数据: ({}, {})", psChunkNVo.chunkX(), psChunkNVo.chunkZ());
        clientWorld.handleChunkData(psChunkNVo);
    }
    
    /**
     * 处理进程切换玩家数据 (PsPlayerNVo)
     */
    private void handleProcessSwitchPlayer(ClientNetworkSession session, PsPlayerNVo psPlayerNVo) {
        if (session.getStage() != ClientNetworkSession.Stage.PROCESS_SWITCHING) {
            log.warn("收到进程切换玩家数据，但当前阶段不正确: {}", session.getStage());
            return;
        }
        
        log.info("收到进程切换玩家数据: 玩家={}, 位置=({}, {}, {})", 
                psPlayerNVo.name(), psPlayerNVo.posX(), psPlayerNVo.posY(), psPlayerNVo.posZ());
        
        // 创建玩家 (ClientWorld应该已经在handleProcessSwitchRequest中创建)
        if (clientWorld == null) {
            log.error("ClientWorld为null，无法创建玩家。尝试补救...");
            WorldTemplate template = Registry.getInstance().getWorldTemplate(EngineDefault.DEFAULT_WORLD_TEMPLATE);
            if (template != null) {
                clientWorld = new ClientWorld(template);
                worldRenderer = new WorldRenderer(clientWorld);
                worldRenderer.init();
            } else {
                return;
            }
        }
        
        if (player == null) {
            ClientPlayer newPlayer = new ClientPlayer(clientWorld);
            Vector3d spawnPos = new Vector3d(psPlayerNVo.posX(), psPlayerNVo.posY(), psPlayerNVo.posZ());
            newPlayer.setPosition(spawnPos);
            newPlayer.setYaw((float) psPlayerNVo.yaw());
            newPlayer.setPitch((float) psPlayerNVo.pitch());
            newPlayer.setHealth(psPlayerNVo.health());
            newPlayer.setHunger((float) psPlayerNVo.hungry());
            newPlayer.initializeCamera();
            
            setGameWorld(clientWorld, worldRenderer, newPlayer);
        } else {
            // 更新玩家状态
            player.setPosition(new Vector3d(psPlayerNVo.posX(), psPlayerNVo.posY(), psPlayerNVo.posZ()));
            player.setYaw((float) psPlayerNVo.yaw());
            player.setPitch((float) psPlayerNVo.pitch());
            player.setHealth(psPlayerNVo.health());
            player.setHunger((float) psPlayerNVo.hungry());
        }
        
        // 所有数据接收完毕，发送完成信号
        session.setStage(ClientNetworkSession.Stage.PROCESS_SWITCHED);
        session.sendNext(new PsFinishNDto());
        log.info("已发送进程切换完成信号");
    }
    
    /**
     * 处理进程切换完成 (PsJoinWorldNVo)
     */
    private void handleProcessSwitchComplete(ClientNetworkSession session) {
        if (session.getStage() != ClientNetworkSession.Stage.PROCESS_SWITCHED) {
            log.warn("收到进入世界通知，但当前阶段不正确: {}", session.getStage());
            return;
        }
        
        session.setStage(ClientNetworkSession.Stage.IN_WORLD);
        playerInitialized = true;
        log.info("进程切换完成，已进入世界");
    }
    
    /**
     * 处理实体位置和旋转更新 (ServerSyncEntityPositionAndRotationNVo)
     */
    private void handleEntityPositionAndRotation(ServerSyncEntityPositionAndRotationNVo packet) {
        if (packet.entityId() == 0) {
            // entityId为0表示玩家自己
            ClientPlayer player = getPlayer();
            if (player != null) {
                Vector3d serverPos = new Vector3d((float) packet.x(), (float) packet.y(), (float) packet.z());
                double distance = player.getPosition().distance(serverPos);
                
                // 如果玩家尚未初始化完成，直接同步到服务端位置（避免初始位置不同步）
                if (!playerInitialized) {
                    player.setPosition(serverPos);
                    player.setYaw(packet.yaw());
                    player.setPitch(packet.pitch());
                    return;
                }

                // 如果玩家已初始化，校准位置
                if (playerInitialized) {
                    // 保存当前位置用于插值
                    player.getPreviousPosition().set(player.getPosition());

                    // 本地与远程位置差异较大，直接同步人物到服务端位置
                    if (distance > 1.0f) {
                        player.getPosition().set(serverPos);
                        // 同时重置速度，避免继续预测错误的方向
                        player.getVelocity().set(0, 0, 0);
                        return;
                    }

                    // 本地与远程位置差异较小，平滑插值到服务器位置
                    if (distance > 0.1f) {
                        player.getPosition().lerp(serverPos, 0.5f);
                        return;
                    }

                    // 如果差异很小（<0.1），不进行校正，保持客户端预测
                    // 同步相机朝向（服务端是权威的）
                    player.setYaw(packet.yaw());
                    player.setPitch(packet.pitch());
                }
            }
        }
        log.debug("收到实体位置更新: entityId={}, pos=({}, {}, {})", packet.entityId(), packet.x(), packet.y(), packet.z());
    }
    
    /**
     * 处理玩家状态更新 (ServerSyncPlayerStateNVo)
     */
    private void handlePlayerState(ServerSyncPlayerStateNVo packet) {
        ClientPlayer player = getPlayer();
        if (player != null) {
            player.setHealth(packet.health());
            // foodLevel 对应饥饿值
            player.setHunger((float) packet.foodLevel());
            
            // 收到玩家状态同步包后，标记玩家已初始化完成
            if (!playerInitialized) {
                playerInitialized = true;
                log.info("玩家初始化完成，可以开始发送位置更新");
            }
        }
        log.debug("收到玩家状态更新: health={}, food={}", packet.health(), packet.foodLevel());
    }
    
    /**
     * 处理世界时间更新 (ServerSyncWorldTimeNVo)
     */
    private void handleWorldTime(ServerSyncWorldTimeNVo packet) {
        if (clientWorld != null) {
            // 将游戏时间（tick）转换为一天中的时间（0.0-1.0）
            // 假设一天有24000 tick（与ServerWorld的TICKS_PER_DAY一致）
            long ticksPerDay = 24000L;
            float timeOfDay = (float) (packet.worldTime() % ticksPerDay) / ticksPerDay;
            clientWorld.setTimeOfDay(timeOfDay);
            log.debug("收到世界时间更新: {} -> {}", packet.worldTime(), timeOfDay);
        }
    }
    
    /**
     * 处理服务器断开连接 (ServerDisconnectNVo)
     */
    private void handleDisconnect(ServerDisconnectNVo packet) {
        log.warn("服务器断开连接: {}", packet.reason());
        if (clientNetworkService != null) {
            clientNetworkService.disconnect();
        }
        stopGame();
    }

    private void updateMainMenu() {
        int result = mainMenu.handleInput(input, window.getWidth(), window.getHeight());
        if (result == 1) {
            currentState = GameState.SINGLEPLAYER_MENU;
        }
        if (result == 2) {
            // 多人游戏按钮被点击
            try {
                var future = clientNetworkService.connect("127.0.0.1", 25564);
                if (future != null) {
                    // 连接是异步的，会在后台完成
                    // 连接成功后，服务端会主动发送进程切换数据包
                    log.info("正在连接到服务器...");
                } else {
                    log.error("连接服务器失败");
                }
            } catch (Exception e) {
                log.error("连接服务器失败", e);
            }
        }
        if (result == 4) {
            running = false;
        }
    }

    private void updateSingleplayerMenu() {
        int result = singleplayerMenu.handleInput(input, window.getWidth(), window.getHeight());
        if (result == 1) {
            currentState = GameState.CREATE_WORLD;
            createWorldMenu.reset();
        }
        if (result == 2) {
            currentState = GameState.MAIN_MENU;
        }
        if (result == 3) {
            String selectedSave = singleplayerMenu.getSelectedSave();
            String selectedWorld = singleplayerMenu.getSelectedWorld();
            if (selectedSave != null && selectedWorld != null) {
                //ClientLauncher.startGameServer(selectedSave, selectedWorld);
            }
        }
    }

    private void updateCreateWorldMenu() {
        int result = createWorldMenu.handleInput(input, window.getWidth(), window.getHeight());
        if (result == 1) {
            String worldName = createWorldMenu.getWorldName();
            String saveName = createWorldMenu.getSaveName();
            if (worldName != null && !worldName.isEmpty() && saveName != null && !saveName.isEmpty()) {
                //ClientLauncher.startGameServer(saveName, worldName);
            }
        }
        if (result == 2) {
            currentState = GameState.SINGLEPLAYER_MENU;
            createWorldMenu.reset();
        }
    }

    private void updateInGame() {
        if (input.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)) {
            currentState = GameState.PAUSED;
            input.setMouseLocked(false);
            return;
        }
        
        if (player != null && input != null) {
            // 优先使用网络连接，如果未连接则使用EventQueue（兼容单人游戏）
            ClientNetworkSession session = clientNetworkService != null ? clientNetworkService.getSession() : null;
            boolean useNetwork = session != null && session.getStage() == ClientNetworkSession.Stage.IN_WORLD;
            
            // 处理滚轮和快捷栏切换（这些不需要固定时间步长）
            double scrollY = input.getScrollY();
            if (scrollY != 0) {
                if (useNetwork && session != null) {
                    int newSlot = player.getInventory().getSelectedSlot() + (int) -scrollY;
                    session.sendNext(new PlayerDshsNdto(newSlot));
                } else {
                    SimpleEventQueue.getInstance().offerC2S(new PlayerHotbarSwitchEvent((int) -scrollY));
                }
            }
            
            // 处理数字键1-9切换快捷栏
            for (int key = org.lwjgl.glfw.GLFW.GLFW_KEY_1; key <= org.lwjgl.glfw.GLFW.GLFW_KEY_9; key++) {
                if (input.isKeyPressed(key)) {
                    int slotIndex = key - org.lwjgl.glfw.GLFW.GLFW_KEY_1;
                    if (useNetwork && session != null) {
                        session.sendNext(new PlayerDshsNdto(slotIndex));
                    } else {
                        int currentSlot = player.getInventory().getSelectedSlot();
                        int slotDelta = slotIndex - currentSlot;
                        if (slotDelta != 0) {
                            SimpleEventQueue.getInstance().offerC2S(new PlayerHotbarSwitchEvent(slotDelta));
                        }
                    }
                }
            }
            
            // 处理鼠标点击（方块破坏和放置）
            if (input.isMouseButtonPressed(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
                if (useNetwork && session != null) {
                    // TODO: 获取目标方块位置和面
                    session.sendNext(new PlayerDActionNDto(ActionType.FINISH_BREAKING, 0, 0, 0, 0));
                } else {
                    SimpleEventQueue.getInstance().offerC2S(new PlayerActionEvent(PlayerAction.ATTACK));
                }
            }
            if (input.isMouseButtonPressed(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
                if (useNetwork && session != null) {
                    // TODO: 获取目标方块位置和面
                    session.sendNext(new PlayerDActionNDto(ActionType.PLACE_BLOCK, 0, 0, 0, 0));
                } else {
                    SimpleEventQueue.getInstance().offerC2S(new PlayerActionEvent(PlayerAction.USE));
                }
            }
        }
        
        // 处理网格生成（这些不需要固定时间步长）
        if (clientWorld != null) {
            clientWorld.processMeshGeneration();
            timeOfDay = clientWorld.getTimeOfDay();
        }
    }

    private void updatePaused() {
        if (input.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)) {
            currentState = GameState.IN_GAME;
            input.setMouseLocked(true);
        }
    }

    private void render() {
        if (window.isResized()) {
            renderer.resize(window.getWidth(), window.getHeight());
        }

        if (currentState == GameState.MAIN_MENU || 
            currentState == GameState.SINGLEPLAYER_MENU ||
            currentState == GameState.CREATE_WORLD) {
            org.lwjgl.opengl.GL11.glClearColor(0.1f, 0.1f, 0.15f, 1.0f);
            org.lwjgl.opengl.GL11.glClear(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT | org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT);
        } else {
            renderer.clear();
        }

        if (currentState == GameState.MAIN_MENU) {
            renderMainMenu();
            return;
        }
        
        if (currentState == GameState.SINGLEPLAYER_MENU) {
            renderSingleplayerMenu();
            return;
        }
        
        if (currentState == GameState.CREATE_WORLD) {
            renderCreateWorldMenu();
            return;
        }
        
        if (currentState == GameState.IN_GAME) {
            renderInGame();
            return;
        }
        
        if (currentState == GameState.PAUSED) {
            renderInGame();
            renderPausedMenu();
            return;
        }
    }

    private void renderMainMenu() {
        if (guiRenderer == null) {
            log.error("GameClient: GuiRenderer未初始化");
            return;
        }
        if (mainMenu == null) {
            log.error("GameClient: MainMenu未初始化");
            return;
        }
        mainMenu.render(guiRenderer, window.getWidth(), window.getHeight(), input);
        window.update();
    }

    private void renderSingleplayerMenu() {
        singleplayerMenu.render(guiRenderer, window.getWidth(), window.getHeight(), input);
        window.update();
    }

    private void renderCreateWorldMenu() {
        createWorldMenu.render(guiRenderer, window.getWidth(), window.getHeight(), input);
        window.update();
    }

    private void renderInGame() {
        if (worldRenderer != null && renderer != null && guiRenderer != null && clientWorld != null) {
            renderer.initHud(guiRenderer, worldRenderer.getTextureId(), clientWorld);
        }
        if (worldRenderer != null && player != null) {
            renderer.render(worldRenderer, player, window.getWidth(), window.getHeight());
        }
        window.update();
    }

    private void renderPausedMenu() {
        org.joml.Vector4f overlay = new org.joml.Vector4f(0.0f, 0.0f, 0.0f, UiConstants.PAUSED_MENU_OVERLAY_ALPHA);
        guiRenderer.renderQuad(0, 0, window.getWidth(), window.getHeight(), overlay, window.getWidth(), window.getHeight());
        
        float centerX = window.getWidth() / 2.0f;
        float centerY = window.getHeight() / 2.0f;
        float buttonWidth = UiConstants.PAUSED_MENU_BUTTON_WIDTH;
        float buttonHeight = UiConstants.PAUSED_MENU_BUTTON_HEIGHT;
        float buttonX = centerX - buttonWidth / 2.0f;
        float buttonSpacing = UiConstants.PAUSED_MENU_BUTTON_SPACING;
        
        org.joml.Vector2d mousePos = input.getMousePosition();
        float resumeButtonY = centerY - buttonSpacing / 2.0f;
        float mainMenuButtonY = centerY + buttonSpacing / 2.0f;
        boolean resumeHovered = isMouseOverButton(mousePos.x, mousePos.y, buttonX, resumeButtonY, buttonWidth, buttonHeight);
        boolean mainMenuHovered = isMouseOverButton(mousePos.x, mousePos.y, buttonX, mainMenuButtonY, buttonWidth, buttonHeight);
        
        guiRenderer.renderButton(buttonX, resumeButtonY, buttonWidth, buttonHeight, "继续游戏", resumeHovered, window.getWidth(), window.getHeight());
        guiRenderer.renderButton(buttonX, mainMenuButtonY, buttonWidth, buttonHeight, "返回主菜单", mainMenuHovered, window.getWidth(), window.getHeight());
        
        if (input.isMouseButtonPressed(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
            if (resumeHovered) {
                currentState = GameState.IN_GAME;
                input.setMouseLocked(true);
            }
            if (mainMenuHovered) {
                stopGame();
                currentState = GameState.MAIN_MENU;
            }
        }
        
        window.update();
    }

    private boolean isMouseOverButton(double mouseX, double mouseY, float buttonX, float buttonY, float buttonWidth, float buttonHeight) {
        return mouseX >= buttonX && mouseX <= buttonX + buttonWidth &&
               mouseY >= buttonY && mouseY <= buttonY + buttonHeight;
    }

    /**
     * 请求初始化多人游戏世界（由网络包处理从网络线程调用）
     * 实际初始化会在主线程的update循环中执行
     */
    public void requestInitializeMultiplayerWorld(double x, double y, double z, float yaw, float pitch) {
        pendingMultiplayerInit = new PendingMultiplayerInit(x, y, z, yaw, pitch);
    }
    
    /**
     * 初始化多人游戏世界（必须在主线程调用）
     */
    private void initializeMultiplayerWorld(double x, double y, double z, float yaw, float pitch) {
        if (clientWorld != null || worldRenderer != null || player != null) {
            log.warn("游戏世界已存在，先清理");
            stopGame();
        }

        // 获取默认世界模板
        WorldTemplate template = Registry.getInstance().getWorldTemplate(EngineDefault.DEFAULT_WORLD_TEMPLATE);

        if (template == null) {
            log.error("无法初始化多人游戏世界: 默认模板未找到");
            return;
        }

        // 创建客户端世界
        ClientWorld newClientWorld = new ClientWorld(template);
        WorldRenderer newWorldRenderer = new WorldRenderer(newClientWorld);
        newWorldRenderer.init();
        
        // 创建玩家
        ClientPlayer newPlayer = new ClientPlayer(newClientWorld);
        Vector3d spawnPos = new Vector3d(x, y, z);
        newPlayer.setPosition(spawnPos); // 使用setPosition确保previousPosition也被正确初始化
        newPlayer.setYaw(yaw);
        newPlayer.setPitch(pitch);
        newPlayer.initializeCamera();
        
        // 设置游戏世界
        setGameWorld(newClientWorld, newWorldRenderer, newPlayer);
        
        // 重置初始化标志，等待服务端同步
        playerInitialized = false;
        
        log.info("多人游戏世界初始化完成，玩家位置: ({}, {}, {})", x, y, z);
    }
    
    /**
     * 由Launcher调用，用于设置游戏世界和玩家
     */
    public void setGameWorld(ClientWorld clientWorld, WorldRenderer worldRenderer, ClientPlayer player) {
        this.clientWorld = clientWorld;
        this.worldRenderer = worldRenderer;
        this.player = player;
        clientWorld.setPlayer(player);
        
        this.currentState = GameState.IN_GAME;
        if (input != null) {
            input.setMouseLocked(true);
        }
        
        // 如果已连接到服务器，发送ClientReady数据包
        ClientNetworkSession session = clientNetworkService != null ? clientNetworkService.getSession() : null;
        if (session != null && session.getStage() == ClientNetworkSession.Stage.IN_WORLD) {
            session.sendNext(new ClientReadyNDto());
            // 多人游戏模式下，等待服务端同步后再设置初始化标志
            playerInitialized = false;
        } else {
            SimpleEventQueue.getInstance().offerC2S(new ClientReadyEvent());
            // 单人游戏模式下，立即设置初始化标志
            playerInitialized = true;
        }
    }

    /**
     * 获取客户端玩家对象
     */
    public ClientPlayer getPlayer() {
        return player;
    }
    
    /**
     * 检查玩家是否已初始化完成
     */
    public boolean isPlayerInitialized() {
        return playerInitialized;
    }
    
    /**
     * 设置玩家初始化完成状态
     */
    public void setPlayerInitialized(boolean initialized) {
        this.playerInitialized = initialized;
    }

    /**
     * 停止游戏，返回主菜单
     */
    public void stopGame() {
        //ClientLauncher.stopGameServer();
        
        if (clientNetworkService != null) {
            clientNetworkService.disconnect();
        }
        
        if (worldRenderer != null) {
            worldRenderer.cleanup();
            worldRenderer = null;
        }
        if (clientWorld != null) {
            clientWorld.cleanup();
            clientWorld = null;
        }
        player = null;
        timeOfDay = 0.0f;
        playerInitialized = false;
        
        if (input != null) {
            input.setMouseLocked(false);
        }
    }


    private void cleanup() {
        stopGame();
        if (renderer != null) {
            renderer.cleanup();
        }
        if (guiRenderer != null) {
            guiRenderer.cleanup();
        }
        if (window != null) {
            window.cleanup();
        }
    }

    /**
     * 注册所有引擎原版的内容(服务端) 这包括方块、物品、世界模板、实体
     */
    public void registerAllDefaultContent() {

        var registry = Registry.getInstance();

        BlockEnums.registerBlocks(registry);
        WorldTemplateEnums.registerWorldTemplate(registry);

        //注册地形生成器
        var gen = new DefaultTerrainGenerator();
        gen.addLayer(new BaseDensityLayer());
        gen.addLayer(new WaterLayer());
        gen.addLayer(new SurfaceLayer());
        gen.addLayer(new FeatureLayer());
        registry.registerTerrainGenerator(gen);
        
        //注册出生平台生成器
        var spawnPlatformGen = new SpawnPlatformGenerator();
        registry.registerTerrainGenerator(spawnPlatformGen);
    }
}

