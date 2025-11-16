package com.ksptool.ourcraft.client;

import com.ksptool.ourcraft.sharedcore.GameState;
import com.ksptool.ourcraft.client.world.ClientWorld;
import com.ksptool.ourcraft.client.entity.ClientPlayer;
import com.ksptool.ourcraft.client.rendering.Renderer;
import com.ksptool.ourcraft.client.rendering.GuiRenderer;
import com.ksptool.ourcraft.client.rendering.WorldRenderer;
import com.ksptool.ourcraft.sharedcore.block.SharedBlock;
import com.ksptool.ourcraft.client.gui.MainMenu;
import com.ksptool.ourcraft.client.gui.SingleplayerMenu;
import com.ksptool.ourcraft.client.gui.CreateWorldMenu;
import com.ksptool.ourcraft.client.gui.UiConstants;
import com.ksptool.ourcraft.sharedcore.events.EventQueue;
import com.ksptool.ourcraft.sharedcore.events.PlayerInputEvent;
import com.ksptool.ourcraft.sharedcore.events.PlayerHotbarSwitchEvent;
import com.ksptool.ourcraft.sharedcore.events.PlayerActionEvent;
import com.ksptool.ourcraft.sharedcore.events.PlayerAction;
import com.ksptool.ourcraft.sharedcore.events.PlayerCameraInputEvent;
import com.ksptool.ourcraft.sharedcore.events.ClientReadyEvent;
import com.ksptool.ourcraft.sharedcore.world.GlobalPalette;
import com.ksptool.ourcraft.sharedcore.world.Registry;
import com.ksptool.ourcraft.sharedcore.world.WorldTemplate;
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
    
    private float timeOfDay = 0.0f;

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

        running = true;
    }

    private void registerDefaultWorldTemplate() {
        WorldTemplate overworldTemplate = WorldTemplate.builder()
            .templateId("mycraft:overworld")
            .ticksPerSecond(20)
            .gravity(-9.8f)
            .build();
        Registry.registerWorldTemplate(overworldTemplate);
    }

    public void run() {
        init();
        loop();
        cleanup();
    }

    private void loop() {
        double lastTime = System.nanoTime();
        long timer = System.currentTimeMillis();
        int updates = 0;
        int frames = 0;

        while (running && !window.shouldClose()) {
            double now = System.nanoTime();
            double deltaSeconds = (now - lastTime) / 1_000_000_000.0;
            lastTime = now;

            if (deltaSeconds > 0.1) {
                deltaSeconds = 0.1;
            }

            input.update();

            update((float) deltaSeconds);
            updates++;

            render();
            frames++;

            if (System.currentTimeMillis() - timer > 1000) {
                timer += 1000;
                frames = 0;
                updates = 0;
            }
        }
    }

    private void update(float delta) {
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
            updateInGame(delta);
            return;
        }
        
        if (currentState == GameState.PAUSED) {
            updatePaused();
            return;
        }
    }

    private void updateMainMenu() {
        int result = mainMenu.handleInput(input, window.getWidth(), window.getHeight());
        if (result == 1) {
            currentState = GameState.SINGLEPLAYER_MENU;
        }
        if (result == 3) {
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
                com.ksptool.ourcraft.Launcher.startGameServer(selectedSave, selectedWorld);
            }
        }
    }

    private void updateCreateWorldMenu() {
        int result = createWorldMenu.handleInput(input, window.getWidth(), window.getHeight());
        if (result == 1) {
            String worldName = createWorldMenu.getWorldName();
            String saveName = createWorldMenu.getSaveName();
            if (worldName != null && !worldName.isEmpty() && saveName != null && !saveName.isEmpty()) {
                com.ksptool.ourcraft.Launcher.startGameServer(saveName, worldName);
            }
        }
        if (result == 2) {
            currentState = GameState.SINGLEPLAYER_MENU;
            createWorldMenu.reset();
        }
    }

    private void updateInGame(float delta) {
        if (input.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)) {
            currentState = GameState.PAUSED;
            input.setMouseLocked(false);
            return;
        }

        if (player != null && input != null) {
            org.joml.Vector2d mouseDelta = input.getMouseDelta();
            if (mouseDelta.x != 0 || mouseDelta.y != 0) {
                float mouseSensitivity = 0.1f;
                float deltaYaw = (float) mouseDelta.x * mouseSensitivity;
                float deltaPitch = (float) mouseDelta.y * mouseSensitivity;
                EventQueue.getInstance().offerC2S(new PlayerCameraInputEvent(deltaYaw, deltaPitch));
            }
            
            boolean forward = input.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_W);
            boolean backward = input.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_S);
            boolean left = input.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_A);
            boolean right = input.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_D);
            boolean jump = input.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE);
            
            EventQueue eventQueue = EventQueue.getInstance();
            eventQueue.offerC2S(new PlayerInputEvent(forward, backward, left, right, jump));
            
            double scrollY = input.getScrollY();
            if (scrollY != 0) {
                eventQueue.offerC2S(new PlayerHotbarSwitchEvent((int) -scrollY));
            }
            
            // 处理数字键1-9切换快捷栏
            for (int key = org.lwjgl.glfw.GLFW.GLFW_KEY_1; key <= org.lwjgl.glfw.GLFW.GLFW_KEY_9; key++) {
                if (input.isKeyPressed(key)) {
                    int slotIndex = key - org.lwjgl.glfw.GLFW.GLFW_KEY_1;
                    int currentSlot = player.getInventory().getSelectedSlot();
                    int slotDelta = slotIndex - currentSlot;
                    if (slotDelta != 0) {
                        eventQueue.offerC2S(new PlayerHotbarSwitchEvent(slotDelta));
                    }
                }
            }
            
            if (input.isMouseButtonPressed(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
                eventQueue.offerC2S(new PlayerActionEvent(PlayerAction.ATTACK));
            }
            if (input.isMouseButtonPressed(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
                eventQueue.offerC2S(new PlayerActionEvent(PlayerAction.USE));
            }
        }
        
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
        
        EventQueue.getInstance().offerC2S(new ClientReadyEvent());
    }

    /**
     * 停止游戏，返回主菜单
     */
    public void stopGame() {
        com.ksptool.ourcraft.Launcher.stopGameServer();
        
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

