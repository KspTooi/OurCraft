package com.ksptool.ourcraft.client;

import com.ksptool.ourcraft.ClientLauncher;
import com.ksptool.ourcraft.sharedcore.GameState;
import com.ksptool.ourcraft.client.world.ClientWorld;
import com.ksptool.ourcraft.client.entity.ClientPlayer;
import com.ksptool.ourcraft.client.rendering.Renderer;
import com.ksptool.ourcraft.client.rendering.GuiRenderer;
import com.ksptool.ourcraft.client.rendering.WorldRenderer;
import com.ksptool.ourcraft.sharedcore.blocks.inner.SharedBlock;
import com.ksptool.ourcraft.client.gui.MainMenu;
import com.ksptool.ourcraft.client.gui.SingleplayerMenu;
import com.ksptool.ourcraft.client.gui.CreateWorldMenu;
import com.ksptool.ourcraft.client.gui.UiConstants;
import com.ksptool.ourcraft.client.network.ServerConnection;
import com.ksptool.ourcraft.sharedcore.events.EventQueue;
import com.ksptool.ourcraft.sharedcore.events.PlayerInputEvent;
import com.ksptool.ourcraft.sharedcore.events.PlayerHotbarSwitchEvent;
import com.ksptool.ourcraft.sharedcore.events.PlayerActionEvent;
import com.ksptool.ourcraft.sharedcore.events.PlayerAction;
import com.ksptool.ourcraft.sharedcore.events.PlayerCameraInputEvent;
import com.ksptool.ourcraft.sharedcore.events.ClientReadyEvent;
import com.ksptool.ourcraft.sharedcore.network.packets.*;
import com.ksptool.ourcraft.sharedcore.world.GlobalPalette;
import com.ksptool.ourcraft.sharedcore.world.Registry;
import com.ksptool.ourcraft.sharedcore.world.WorldTemplateOld;
import lombok.extern.slf4j.Slf4j;

/**
 * 游戏客户端，负责渲染、输入处理和事件消费
 */
@Slf4j
public class GameClient {

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
    
    private ServerConnection serverConnection;
    
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
        registerDefaultWorldTemplate();
        SharedBlock.registerBlocks();
        GlobalPalette.getInstance().bake();
        
        window = new Window(1280, 720, "OurCraft 1.1Z1 \uD83E\uDD86 内部预览版");
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
        
        serverConnection = new ServerConnection(this);

        running = true;
    }

    private void registerDefaultWorldTemplate() {
        WorldTemplateOld overworldTemplateOld = WorldTemplateOld.builder()
            .templateId("mycraft:overworld")
            .ticksPerSecond(20)
            .gravity(-9.8f)
            .build();
        Registry.registerWorldTemplateOld(overworldTemplateOld);
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
                tickRate = clientWorld.getTemplate().getTicksPerSecond();
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
        boolean useNetwork = serverConnection != null && serverConnection.isConnected();
        
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
        if (useNetwork) {
            clientTick++;
            serverConnection.sendPacket(new PlayerInputStateNDto(
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
            EventQueue eventQueue = EventQueue.getInstance();
            eventQueue.offerC2S(inputEvent);
            
            org.joml.Vector2d mouseDelta = input.getMouseDelta();
            if (mouseDelta.x != 0 || mouseDelta.y != 0) {
                float mouseSensitivity = 0.1f;
                float deltaYaw = (float) mouseDelta.x * mouseSensitivity;
                float deltaPitch = (float) mouseDelta.y * mouseSensitivity;
                eventQueue.offerC2S(new PlayerCameraInputEvent(deltaYaw, deltaPitch));
            }
        }
    }
    
    /**
     * 处理非物理相关的更新（UI状态切换、数据包处理等）
     */
    private void updateNonPhysics() {
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

    private void updateMainMenu() {
        // 处理来自服务器的数据包（必须在主菜单也处理，以便接收加入服务器的响应）
        if (serverConnection != null && serverConnection.isConnected()) {
            serverConnection.processPackets();
        }
        
        int result = mainMenu.handleInput(input, window.getWidth(), window.getHeight());
        if (result == 1) {
            currentState = GameState.SINGLEPLAYER_MENU;
        }
        if (result == 2) {
            // 多人游戏按钮被点击
            if (serverConnection.connect("127.0.0.1", 25564)) {
                // 连接成功，发送加入服务器请求
                serverConnection.sendPacket(new RequestJoinServerNDto("1.1W", "KspTooi"));
            } else {
                log.error("连接服务器失败");
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
                ClientLauncher.startGameServer(selectedSave, selectedWorld);
            }
        }
    }

    private void updateCreateWorldMenu() {
        int result = createWorldMenu.handleInput(input, window.getWidth(), window.getHeight());
        if (result == 1) {
            String worldName = createWorldMenu.getWorldName();
            String saveName = createWorldMenu.getSaveName();
            if (worldName != null && !worldName.isEmpty() && saveName != null && !saveName.isEmpty()) {
                ClientLauncher.startGameServer(saveName, worldName);
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
        
        // 处理来自服务器的数据包（必须在主线程中处理）
        if (serverConnection != null && serverConnection.isConnected()) {
            serverConnection.processPackets();
        }

        if (player != null && input != null) {
            // 优先使用网络连接，如果未连接则使用EventQueue（兼容单人游戏）
            boolean useNetwork = serverConnection != null && serverConnection.isConnected();
            
            // 处理滚轮和快捷栏切换（这些不需要固定时间步长）
            double scrollY = input.getScrollY();
            if (scrollY != 0) {
                if (useNetwork) {
                    int newSlot = player.getInventory().getSelectedSlot() + (int) -scrollY;
                    serverConnection.sendPacket(new PlayerDshsNdto(newSlot));
                } else {
                    EventQueue.getInstance().offerC2S(new PlayerHotbarSwitchEvent((int) -scrollY));
                }
            }
            
            // 处理数字键1-9切换快捷栏
            for (int key = org.lwjgl.glfw.GLFW.GLFW_KEY_1; key <= org.lwjgl.glfw.GLFW.GLFW_KEY_9; key++) {
                if (input.isKeyPressed(key)) {
                    int slotIndex = key - org.lwjgl.glfw.GLFW.GLFW_KEY_1;
                    if (useNetwork) {
                        serverConnection.sendPacket(new PlayerDshsNdto(slotIndex));
                    } else {
                        int currentSlot = player.getInventory().getSelectedSlot();
                        int slotDelta = slotIndex - currentSlot;
                        if (slotDelta != 0) {
                            EventQueue.getInstance().offerC2S(new PlayerHotbarSwitchEvent(slotDelta));
                        }
                    }
                }
            }
            
            // 处理鼠标点击（方块破坏和放置）
            if (input.isMouseButtonPressed(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
                if (useNetwork) {
                    // TODO: 获取目标方块位置和面
                    serverConnection.sendPacket(new PlayerDActionNDto(ActionType.FINISH_BREAKING, 0, 0, 0, 0));
                } else {
                    EventQueue.getInstance().offerC2S(new PlayerActionEvent(PlayerAction.ATTACK));
                }
            }
            if (input.isMouseButtonPressed(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
                if (useNetwork) {
                    // TODO: 获取目标方块位置和面
                    serverConnection.sendPacket(new PlayerDActionNDto(ActionType.PLACE_BLOCK, 0, 0, 0, 0));
                } else {
                    EventQueue.getInstance().offerC2S(new PlayerActionEvent(PlayerAction.USE));
                }
            }
        }
        
        // 处理世界事件和网格生成（这些不需要固定时间步长）
        if (clientWorld != null) {
            clientWorld.processEvents();
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
     * 请求初始化多人游戏世界（由ServerConnection从网络线程调用）
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
        WorldTemplateOld template = Registry.getWorldTemplateOld("mycraft:overworld");
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
        org.joml.Vector3f spawnPos = new org.joml.Vector3f((float) x, (float) y, (float) z);
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
        
        if (serverConnection != null) {
            serverConnection.setClientWorld(clientWorld);
        }
        
        this.currentState = GameState.IN_GAME;
        if (input != null) {
            input.setMouseLocked(true);
        }
        
        // 如果已连接到服务器，发送ClientReady数据包
        if (serverConnection != null && serverConnection.isConnected()) {
            serverConnection.sendPacket(new ClientReadyNDto());
            // 多人游戏模式下，等待服务端同步后再设置初始化标志
            playerInitialized = false;
        } else {
            EventQueue.getInstance().offerC2S(new ClientReadyEvent());
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
        ClientLauncher.stopGameServer();
        
        if (serverConnection != null) {
            serverConnection.disconnect();
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
}

