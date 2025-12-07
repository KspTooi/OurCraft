package com.ksptool.ourcraft.clientjme.states;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.RawInputListener;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.event.MouseMotionEvent;
import com.ksptool.ourcraft.clientjme.OurCraftClientJme;
import com.ksptool.ourcraft.clientjme.entity.JmeClientPlayer;
import com.ksptool.ourcraft.clientjme.network.JmeClientNetworkService;
import com.ksptool.ourcraft.clientjme.network.JmeClientNetworkSession;
import com.ksptool.ourcraft.clientjme.world.JmeClientWorld;
import com.ksptool.ourcraft.sharedcore.events.PlayerInputEvent;
import com.ksptool.ourcraft.sharedcore.network.nvo.*;
import com.ksptool.ourcraft.sharedcore.network.ndto.PsAllowNDto;
import com.ksptool.ourcraft.sharedcore.network.ndto.PsFinishNDto;
import com.ksptool.ourcraft.sharedcore.network.packets.*;
import com.ksptool.ourcraft.sharedcore.utils.SimpleEventQueue;
import com.ksptool.ourcraft.sharedcore.world.WorldTemplate;
import com.ksptool.ourcraft.sharedcore.enums.EngineDefault;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3d;

/**
 * 游戏状态，处理游戏逻辑、输入和网络同步
 */
@Slf4j
public class GameplayState extends BaseAppState {

    private OurCraftClientJme app;
    private JmeClientWorld clientWorld;
    private JmeClientPlayer player;
    private JmeClientNetworkService clientNetworkService;
    
    private boolean playerInitialized = false;
    private int clientTick = 0;
    
    // 输入状态
    private boolean forward = false;
    private boolean backward = false;
    private boolean left = false;
    private boolean right = false;
    private boolean jump = false;
    private boolean shift = false;
    
    // 鼠标灵敏度（像素到角度的转换系数）
    private float mouseSensitivity = 0.1f;
    
    // 鼠标初始化标志（用于避免首次移动时的跳跃）
    private boolean mouseInitialized = false;
    
    // 固定时间步长
    private double accumulator = 0.0;
    private double tickRate = 20.0;
    private double tickTime = 1.0 / tickRate;
    
    // 待处理的多人游戏世界初始化任务
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

    @Override
    protected void initialize(Application app) {
        this.app = (OurCraftClientJme) app;
        this.clientNetworkService = ((OurCraftClientJme) app).getClientNetworkService();
        
        // 设置输入映射
        setupInputMappings();
        
        // 注册原始鼠标输入监听器
        app.getInputManager().addRawInputListener(rawMouseListener);
        
        // 设置相机
        this.app.getFlyByCamera().setEnabled(false);
        this.app.getCamera().setLocation(new com.jme3.math.Vector3f(0, 5, 0));
        this.app.getCamera().lookAt(new com.jme3.math.Vector3f(0, 0, 0), com.jme3.math.Vector3f.UNIT_Y);
    }

    @Override
    protected void cleanup(Application app) {
        // 清理输入映射
        InputManager inputManager = app.getInputManager();
        inputManager.removeListener(actionListener);
        inputManager.removeRawInputListener(rawMouseListener);
        inputManager.deleteMapping("Forward");
        inputManager.deleteMapping("Backward");
        inputManager.deleteMapping("Left");
        inputManager.deleteMapping("Right");
        inputManager.deleteMapping("Jump");
        inputManager.deleteMapping("Shift");
        
        // 清理世界
        if (clientWorld != null) {
            clientWorld.cleanup();
            clientWorld = null;
        }
        player = null;
    }

    @Override
    protected void onEnable() {
        // 隐藏鼠标光标并禁用飞翔相机
        app.getInputManager().setCursorVisible(false);
        app.getFlyByCamera().setEnabled(false);
        // 重置鼠标位置状态
        mouseInitialized = false;
    }

    @Override
    protected void onDisable() {
        // 恢复鼠标光标可见性
        app.getInputManager().setCursorVisible(true);
    }

    @Override
    public void update(float tpf) {
        // 限制最大时间步长
        double deltaSeconds = Math.min(tpf, 0.1);
        
        // 处理网络数据包
        processNetworkPackets();
        
        // 检查是否有待处理的多人游戏初始化任务
        PendingMultiplayerInit init = pendingMultiplayerInit;
        if (init != null) {
            pendingMultiplayerInit = null;
            initializeMultiplayerWorld(init.x, init.y, init.z, init.yaw, init.pitch);
        }
        
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
        
        // 处理非物理相关的更新（网格生成等）
        if (clientWorld != null) {
            clientWorld.processMeshGeneration();
        }
        
        // 更新相机位置（从玩家位置）
        if (player != null && playerInitialized) {
            com.jme3.math.Vector3f eyePos = new com.jme3.math.Vector3f(
                (float) player.getPosition().x,
                (float) (player.getPosition().y + player.getEyeHeight()),
                (float) player.getPosition().z
            );
            app.getCamera().setLocation(eyePos);
            
            // 更新相机朝向
            float yawRad = (float) Math.toRadians(player.getYaw());
            float pitchRad = (float) Math.toRadians(player.getPitch());
            com.jme3.math.Vector3f direction = new com.jme3.math.Vector3f(
                (float) Math.sin(yawRad) * (float) Math.cos(pitchRad),
                (float) -Math.sin(pitchRad),
                (float) (-Math.cos(yawRad) * Math.cos(pitchRad))
            );
            app.getCamera().lookAt(eyePos.add(direction), com.jme3.math.Vector3f.UNIT_Y);
        }
    }

    /**
     * 固定时间步长的物理更新（tick）
     */
    private void tick(float tickDelta) {
        if (player == null || !playerInitialized) {
            return;
        }
        
        // 应用输入到客户端玩家（本地预测）
        PlayerInputEvent inputEvent = new PlayerInputEvent(forward, backward, left, right, jump);
        player.applyInput(inputEvent);
        
        // 更新玩家物理（客户端预测）
        player.update(tickDelta);
        
        // 发送输入状态到服务器（如果已连接）
        JmeClientNetworkSession session = clientNetworkService != null ? clientNetworkService.getSession() : null;
        if (session != null && session.getStage() == JmeClientNetworkSession.Stage.IN_WORLD) {
            clientTick++;
            session.sendNext(new PlayerInputStateNDto(
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
            SimpleEventQueue.getInstance().offerC2S(inputEvent);
        }
    }

    /**
     * 设置输入映射
     */
    private void setupInputMappings() {
        InputManager inputManager = app.getInputManager();
        inputManager.addMapping("Forward", new KeyTrigger(KeyInput.KEY_W));
        inputManager.addMapping("Backward", new KeyTrigger(KeyInput.KEY_S));
        inputManager.addMapping("Left", new KeyTrigger(KeyInput.KEY_A));
        inputManager.addMapping("Right", new KeyTrigger(KeyInput.KEY_D));
        inputManager.addMapping("Jump", new KeyTrigger(KeyInput.KEY_SPACE));
        inputManager.addMapping("Shift", new KeyTrigger(KeyInput.KEY_LSHIFT), new KeyTrigger(KeyInput.KEY_RSHIFT));
        
        inputManager.addListener(actionListener, "Forward", "Backward", "Left", "Right", "Jump", "Shift");
    }

    private final ActionListener actionListener = (name, isPressed, tpf) -> {
        if (name.equals("Forward")) {
            forward = isPressed;
        } else if (name.equals("Backward")) {
            backward = isPressed;
        } else if (name.equals("Left")) {
            left = isPressed;
        } else if (name.equals("Right")) {
            right = isPressed;
        } else if (name.equals("Jump")) {
            jump = isPressed;
        } else if (name.equals("Shift")) {
            shift = isPressed;
        }
    };

    // 原始鼠标输入监听器，直接获取鼠标位置变化（像素级精度）
    private final RawInputListener rawMouseListener = new RawInputListener() {
        @Override
        public void onMouseMotionEvent(MouseMotionEvent evt) {
            if (player == null || !playerInitialized) {
                return;
            }
            
            float deltaX = evt.getDX();
            float deltaY = evt.getDY();
            
            // 首次移动时初始化，避免跳跃
            if (!mouseInitialized) {
                mouseInitialized = true;
                return;
            }
            
            // 应用灵敏度并传递给玩家
            // Y轴反转：鼠标向上移动时deltaY为负，需要转换为pitch减小（抬头）
            float sensitivityX = deltaX * mouseSensitivity;
            float sensitivityY = -deltaY * mouseSensitivity;
            
            player.handleMouseInput(sensitivityX, sensitivityY);
        }
        
        @Override
        public void beginInput() {}
        
        @Override
        public void endInput() {}
        
        @Override
        public void onJoyAxisEvent(com.jme3.input.event.JoyAxisEvent evt) {}
        
        @Override
        public void onJoyButtonEvent(com.jme3.input.event.JoyButtonEvent evt) {}
        
        @Override
        public void onMouseButtonEvent(com.jme3.input.event.MouseButtonEvent evt) {}
        
        @Override
        public void onKeyEvent(com.jme3.input.event.KeyInputEvent evt) {}
        
        @Override
        public void onTouchEvent(com.jme3.input.event.TouchEvent evt) {}
    };

    /**
     * 处理网络数据包
     */
    private void processNetworkPackets() {
        if (clientNetworkService == null) {
            return;
        }
        
        JmeClientNetworkSession session = clientNetworkService.getSession();
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
    private void handleNetworkPacket(JmeClientNetworkSession session, Object packet) {
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
            return;
        }
        
        log.debug("收到未处理的网络数据包: {}", packet.getClass().getSimpleName());
    }

    private void handleProcessSwitchRequest(JmeClientNetworkSession session, PsNVo psNVo) {
        if (session.getStage() != JmeClientNetworkSession.Stage.PROCESSED) {
            log.warn("收到进程切换请求，但当前阶段不正确: {}", session.getStage());
            return;
        }
        
        log.info("收到进程切换请求: 世界={}, APS={}, TotalActions={}", 
                psNVo.worldName(), psNVo.aps(), psNVo.totalActions());
        
        WorldTemplate template = com.ksptool.ourcraft.sharedcore.Registry.getInstance().getWorldTemplate(EngineDefault.DEFAULT_WORLD_TEMPLATE);
        if (template == null) {
            log.error("无法初始化多人游戏世界: 默认模板未找到");
            return;
        }
        
        JmeClientWorld newClientWorld = new JmeClientWorld(template, app.getRootNode(), app.getAssetManager());
        this.clientWorld = newClientWorld;
        
        session.setStage(JmeClientNetworkSession.Stage.PROCESS_SWITCHING);
        session.sendNext(new PsAllowNDto());
        log.info("已发送进程切换允许信号");
    }

    private void handleProcessSwitchChunk(JmeClientNetworkSession session, PsChunkNVo psChunkNVo) {
        if (session.getStage() != JmeClientNetworkSession.Stage.PROCESS_SWITCHING) {
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

    private void handleProcessSwitchPlayer(JmeClientNetworkSession session, PsPlayerNVo psPlayerNVo) {
        if (session.getStage() != JmeClientNetworkSession.Stage.PROCESS_SWITCHING) {
            log.warn("收到进程切换玩家数据，但当前阶段不正确: {}", session.getStage());
            return;
        }
        
        log.info("收到进程切换玩家数据: 玩家={}, 位置=({}, {}, {})", 
                psPlayerNVo.name(), psPlayerNVo.posX(), psPlayerNVo.posY(), psPlayerNVo.posZ());
        
        if (clientWorld == null) {
            log.error("ClientWorld为null，无法创建玩家");
            return;
        }
        
        if (player == null) {
            JmeClientPlayer newPlayer = new JmeClientPlayer(clientWorld);
            Vector3d spawnPos = new Vector3d(psPlayerNVo.posX(), psPlayerNVo.posY(), psPlayerNVo.posZ());
            newPlayer.setPosition(spawnPos);
            newPlayer.setYaw((float) psPlayerNVo.yaw());
            newPlayer.setPitch((float) psPlayerNVo.pitch());
            newPlayer.setHealth(psPlayerNVo.health());
            newPlayer.setHunger((float) psPlayerNVo.hungry());
            newPlayer.initializeCamera();
            
            this.player = newPlayer;
            clientWorld.setPlayer(newPlayer);
        } else {
            player.setPosition(new Vector3d(psPlayerNVo.posX(), psPlayerNVo.posY(), psPlayerNVo.posZ()));
            player.setYaw((float) psPlayerNVo.yaw());
            player.setPitch((float) psPlayerNVo.pitch());
            player.setHealth(psPlayerNVo.health());
            player.setHunger((float) psPlayerNVo.hungry());
        }
        
        session.setStage(JmeClientNetworkSession.Stage.PROCESS_SWITCHED);
        session.sendNext(new PsFinishNDto());
        log.info("已发送进程切换完成信号");
    }

    private void handleProcessSwitchComplete(JmeClientNetworkSession session) {
        if (session.getStage() != JmeClientNetworkSession.Stage.PROCESS_SWITCHED) {
            log.warn("收到进入世界通知，但当前阶段不正确: {}", session.getStage());
            return;
        }
        
        session.setStage(JmeClientNetworkSession.Stage.IN_WORLD);
        playerInitialized = true;
        log.info("进程切换完成，已进入世界");
    }

    private void handleEntityPositionAndRotation(ServerSyncEntityPositionAndRotationNVo packet) {
        if (packet.entityId() == 0 && player != null) {
            Vector3d serverPos = new Vector3d((float) packet.x(), (float) packet.y(), (float) packet.z());
            double distance = player.getPosition().distance(serverPos);
            
            if (!playerInitialized) {
                player.setPosition(serverPos);
                player.setYaw(packet.yaw());
                player.setPitch(packet.pitch());
                return;
            }

            if (distance > 1.0f) {
                player.getPosition().set(serverPos);
                player.getVelocity().set(0, 0, 0);
                return;
            }

            if (distance > 0.1f) {
                player.getPosition().lerp(serverPos, 0.5f);
                return;
            }

            player.setYaw(packet.yaw());
            player.setPitch(packet.pitch());
        }
    }

    private void handlePlayerState(ServerSyncPlayerStateNVo packet) {
        if (player != null) {
            player.setHealth(packet.health());
            player.setHunger((float) packet.foodLevel());
            
            if (!playerInitialized) {
                playerInitialized = true;
                log.info("玩家初始化完成，可以开始发送位置更新");
            }
        }
    }

    private void handleWorldTime(ServerSyncWorldTimeNVo packet) {
        if (clientWorld != null) {
            long ticksPerDay = 24000L;
            float timeOfDay = (float) (packet.worldTime() % ticksPerDay) / ticksPerDay;
            clientWorld.setTimeOfDay(timeOfDay);
        }
    }

    private void handleDisconnect(ServerDisconnectNVo packet) {
        log.warn("服务器断开连接: {}", packet.reason());
        if (clientNetworkService != null) {
            clientNetworkService.disconnect();
        }
        stopGame();
    }

    private void initializeMultiplayerWorld(double x, double y, double z, float yaw, float pitch) {
        if (clientWorld != null || player != null) {
            log.warn("游戏世界已存在，先清理");
            stopGame();
        }

        WorldTemplate template = com.ksptool.ourcraft.sharedcore.Registry.getInstance().getWorldTemplate(EngineDefault.DEFAULT_WORLD_TEMPLATE);
        if (template == null) {
            log.error("无法初始化多人游戏世界: 默认模板未找到");
            return;
        }

        JmeClientWorld newClientWorld = new JmeClientWorld(template, app.getRootNode(), app.getAssetManager());
        JmeClientPlayer newPlayer = new JmeClientPlayer(newClientWorld);
        Vector3d spawnPos = new Vector3d(x, y, z);
        newPlayer.setPosition(spawnPos);
        newPlayer.setYaw(yaw);
        newPlayer.setPitch(pitch);
        newPlayer.initializeCamera();
        
        this.clientWorld = newClientWorld;
        this.player = newPlayer;
        newClientWorld.setPlayer(newPlayer);
        
        playerInitialized = false;
        log.info("多人游戏世界初始化完成，玩家位置: ({}, {}, {})", x, y, z);
    }

    private void stopGame() {
        if (clientNetworkService != null) {
            clientNetworkService.disconnect();
        }
        
        if (clientWorld != null) {
            clientWorld.cleanup();
            clientWorld = null;
        }
        player = null;
        playerInitialized = false;
    }
}

