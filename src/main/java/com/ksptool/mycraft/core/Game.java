package com.ksptool.mycraft.core;

import com.ksptool.mycraft.world.World;
import com.ksptool.mycraft.entity.Player;
import com.ksptool.mycraft.rendering.Renderer;
import com.ksptool.mycraft.rendering.GuiRenderer;
import com.ksptool.mycraft.world.Block;
import com.ksptool.mycraft.world.GlobalPalette;
import com.ksptool.mycraft.gui.MainMenu;
import com.ksptool.mycraft.gui.SingleplayerMenu;
import com.ksptool.mycraft.gui.CreateWorldMenu;
import com.ksptool.mycraft.world.WorldManager;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 游戏主循环和状态管理类，负责游戏初始化、更新、渲染和状态切换
 */
@Slf4j
public class Game {

    private Window window;
    private Input input;
    private Renderer renderer;
    private GuiRenderer guiRenderer;
    private World world;
    private Player player;
    private boolean running;
    
    private GameState currentState = GameState.MAIN_MENU;
    
    private MainMenu mainMenu;
    private SingleplayerMenu singleplayerMenu;
    private CreateWorldMenu createWorldMenu;

    public void init() {
        Block.registerBlocks();
        GlobalPalette.getInstance().bake();
        
        window = new Window(1280, 720, "MyCraft");
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
        input.update();

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
                loadWorld(selectedSave, selectedWorld);
            }
        }
    }

    private void updateCreateWorldMenu() {
        int result = createWorldMenu.handleInput(input, window.getWidth(), window.getHeight());
        if (result == 1) {
            String worldName = createWorldMenu.getWorldName();
            String saveName = createWorldMenu.getSaveName();
            if (worldName != null && !worldName.isEmpty() && saveName != null && !saveName.isEmpty()) {
                createNewWorld(saveName, worldName);
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

        long entityUpdateStart = System.nanoTime();
        for (com.ksptool.mycraft.entity.Entity entity : world.getEntities()) {
            if (entity instanceof Player) {
                continue;
            }
            entity.update(delta);
        }
        long entityUpdateTime = System.nanoTime() - entityUpdateStart;
        
        long playerUpdateStart = System.nanoTime();
        player.update(input, delta);
        long playerUpdateTime = System.nanoTime() - playerUpdateStart;
        
        long worldUpdateStart = System.nanoTime();
        world.update(player.getPosition());
        long worldUpdateTime = System.nanoTime() - worldUpdateStart;
        
        long meshUploadStart = System.nanoTime();
        com.ksptool.mycraft.world.ChunkMeshGenerator meshGenerator = world.getChunkMeshGenerator();
        if (meshGenerator != null) {
            java.util.List<java.util.concurrent.Future<com.ksptool.mycraft.world.MeshGenerationResult>> futures = meshGenerator.getPendingFutures();
            java.util.List<java.util.concurrent.Future<com.ksptool.mycraft.world.MeshGenerationResult>> completedFutures = new java.util.ArrayList<>();
            for (java.util.concurrent.Future<com.ksptool.mycraft.world.MeshGenerationResult> future : futures) {
                if (future.isDone()) {
                    try {
                        com.ksptool.mycraft.world.MeshGenerationResult result = future.get();
                        if (result != null) {
                            result.chunk.uploadToGPU(result);
                        }
                    } catch (Exception e) {
                        log.error("Error uploading mesh to GPU", e);
                    }
                    completedFutures.add(future);
                }
            }
            futures.removeAll(completedFutures);
        }
        long meshUploadTime = System.nanoTime() - meshUploadStart;
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
            log.error("Game: GuiRenderer未初始化");
            return;
        }
        if (mainMenu == null) {
            log.error("Game: MainMenu未初始化");
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
        if (world != null && renderer != null && guiRenderer != null) {
            renderer.initHud(guiRenderer, world.getTextureId());
        }
        renderer.render(world, player, window.getWidth(), window.getHeight());
        window.update();
    }

    private void renderPausedMenu() {
        org.joml.Vector4f overlay = new org.joml.Vector4f(0.0f, 0.0f, 0.0f, 0.5f);
        guiRenderer.renderQuad(0, 0, window.getWidth(), window.getHeight(), overlay, window.getWidth(), window.getHeight());
        
        float centerX = window.getWidth() / 2.0f;
        float centerY = window.getHeight() / 2.0f;
        float buttonWidth = 200.0f;
        float buttonHeight = 40.0f;
        float buttonX = centerX - buttonWidth / 2.0f;
        
        org.joml.Vector2d mousePos = input.getMousePosition();
        boolean resumeHovered = isMouseOverButton(mousePos.x, mousePos.y, buttonX, centerY - 30.0f, buttonWidth, buttonHeight);
        boolean mainMenuHovered = isMouseOverButton(mousePos.x, mousePos.y, buttonX, centerY + 20.0f, buttonWidth, buttonHeight);
        
        guiRenderer.renderButton(buttonX, centerY - 30.0f, buttonWidth, buttonHeight, "继续游戏", resumeHovered, window.getWidth(), window.getHeight());
        guiRenderer.renderButton(buttonX, centerY + 20.0f, buttonWidth, buttonHeight, "返回主菜单", mainMenuHovered, window.getWidth(), window.getHeight());
        
        if (input.isMouseButtonPressed(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
            if (resumeHovered) {
                currentState = GameState.IN_GAME;
                input.setMouseLocked(true);
            }
            if (mainMenuHovered) {
                cleanupWorld();
                currentState = GameState.MAIN_MENU;
            }
        }
        
        window.update();
    }

    private boolean isMouseOverButton(double mouseX, double mouseY, float buttonX, float buttonY, float buttonWidth, float buttonHeight) {
        return mouseX >= buttonX && mouseX <= buttonX + buttonWidth &&
               mouseY >= buttonY && mouseY <= buttonY + buttonHeight;
    }

    private String currentSaveName = null;
    private String currentWorldName = null;

    public void startGame(String saveName, String worldName) {
        if (world == null) {
            createNewWorld(saveName, worldName);
        }
        
        if (world == null) {
            return;
        }
        
        if (player == null) {
            float initialX = 8.0f;
            float initialZ = 8.0f;
            
            int playerChunkX = (int) Math.floor(initialX / com.ksptool.mycraft.world.Chunk.CHUNK_SIZE);
            int playerChunkZ = (int) Math.floor(initialZ / com.ksptool.mycraft.world.Chunk.CHUNK_SIZE);
            
            for (int x = playerChunkX - 2; x <= playerChunkX + 2; x++) {
                for (int z = playerChunkZ - 2; z <= playerChunkZ + 2; z++) {
                    world.generateChunkSynchronously(x, z);
                }
            }
            
            int groundHeight = world.getHeightAt((int) initialX, (int) initialZ);
            float initialY = groundHeight + 1.0f;
            
            player = new Player(world);
            player.getPosition().set(initialX, initialY, initialZ);
            player.initializeCamera();
            world.addEntity(player);
        }
        
        currentSaveName = saveName;
        currentWorldName = worldName;
        currentState = GameState.IN_GAME;
        input.setMouseLocked(true);
    }

    public void loadWorld(String saveName, String worldName) {
        cleanupWorld();
        
        World loadedWorld = WorldManager.getInstance().loadWorld(saveName, worldName);
        if (loadedWorld == null) {
            return;
        }
        
        world = loadedWorld;
        
        float initialX = 8.0f;
        float initialZ = 8.0f;
        
        int playerChunkX = (int) Math.floor(initialX / com.ksptool.mycraft.world.Chunk.CHUNK_SIZE);
        int playerChunkZ = (int) Math.floor(initialZ / com.ksptool.mycraft.world.Chunk.CHUNK_SIZE);
        
        for (int x = playerChunkX - 2; x <= playerChunkX + 2; x++) {
            for (int z = playerChunkZ - 2; z <= playerChunkZ + 2; z++) {
                world.generateChunkSynchronously(x, z);
            }
        }
        
        int groundHeight = world.getHeightAt((int) initialX, (int) initialZ);
        float initialY = groundHeight + 1.0f;
        
        java.util.UUID playerUUID = com.ksptool.mycraft.world.save.SaveManager.getInstance().findFirstPlayerUUID(saveName);
        if (playerUUID == null) {
            playerUUID = java.util.UUID.randomUUID();
        }
        
        player = new Player(world, playerUUID);
        player.getPosition().set(initialX, initialY, initialZ);
        player.initializeCamera();
        world.addEntity(player);
        
        com.ksptool.mycraft.world.save.PlayerIndex playerIndex = com.ksptool.mycraft.world.save.SaveManager.getInstance().loadPlayer(saveName, playerUUID);
        if (playerIndex != null) {
            player.loadFromPlayerIndex(playerIndex);
            if (player.getPosition().y > 0) {
                initialX = player.getPosition().x;
                initialY = player.getPosition().y;
                initialZ = player.getPosition().z;
            }
        }
        
        currentSaveName = saveName;
        currentWorldName = worldName;
        currentState = GameState.IN_GAME;
        input.setMouseLocked(true);
    }

    public void createNewWorld(String saveName, String worldName) {
        cleanupWorld();
        
        com.ksptool.mycraft.world.save.SaveManager saveManager = com.ksptool.mycraft.world.save.SaveManager.getInstance();
        if (!saveManager.saveExists(saveName)) {
            if (!saveManager.createSave(saveName)) {
                return;
            }
        }
        
        world = new World();
        world.setWorldName(worldName);
        world.setSeed(System.currentTimeMillis());
        world.init();
        
        WorldManager.getInstance().saveWorld(world, saveName, worldName);
        
        startGame(saveName, worldName);
    }

    private void cleanupWorld() {
        if (world != null) {
            if (currentSaveName != null && currentWorldName != null) {
                WorldManager.getInstance().saveWorld(world, player, currentSaveName, currentWorldName);
            }
            world.cleanup();
            world = null;
        }
        player = null;
        currentSaveName = null;
        currentWorldName = null;
    }

    private void cleanup() {
        if (world != null) {
            world.saveAllDirtyData();
        }
        cleanupWorld();
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
