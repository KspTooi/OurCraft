package com.ksptool.ourcraft.debug;

import com.ksptool.ourcraft.sharedcore.network.packets.PlayerInputStateNDto;
import com.ksptool.ourcraft.sharedcore.network.packets.RequestJoinServerNDto;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.event.KeyEvent;

/**
 * 调试客户端主类
 */
@Slf4j
@Getter
public class DebugClient {
    
    private DebugWorld world;
    private DebugNetworkConnection networkConnection;
    private DebugRenderer renderer;
    private JFrame frame;
    
    private double playerX = 0;
    private double playerY = 64;
    private double playerZ = 0;
    private float playerYaw = 0;
    private float playerPitch = 0;
    
    private boolean wPressed = false;
    private boolean sPressed = false;
    private boolean aPressed = false;
    private boolean dPressed = false;
    private boolean spacePressed = false;
    
    private int clientTick = 0;
    private boolean running = false;
    
    public DebugClient() {
        this.world = new DebugWorld();
        this.networkConnection = new DebugNetworkConnection(this);
    }
    
    public void init() {
        frame = new JFrame("调试客户端 - 2D俯视图");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        renderer = new DebugRenderer(this);
        frame.add(renderer);
        
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        
        renderer.requestFocus();
    }
    
    public void start() {
        running = true;
        
        if (!networkConnection.connect("127.0.0.1", 25564)) {
            log.error("无法连接到服务器");
            return;
        }
        
        networkConnection.sendPacket(new RequestJoinServerNDto("1.1W", "DebugClient"));
        
        Thread gameThread = new Thread(this::gameLoop);
        gameThread.setDaemon(true);
        gameThread.start();
    }
    
    private void gameLoop() {
        long lastTime = System.nanoTime();
        double tickRate = 20.0;
        double tickTime = 1.0 / tickRate;
        double accumulator = 0.0;
        
        while (running) {
            long now = System.nanoTime();
            double deltaSeconds = (now - lastTime) / 1_000_000_000.0;
            lastTime = now;
            
            if (deltaSeconds > 0.1) {
                deltaSeconds = 0.1;
            }
            
            accumulator += deltaSeconds;
            
            while (accumulator >= tickTime) {
                tick((float) tickTime);
                accumulator -= tickTime;
            }
            
            SwingUtilities.invokeLater(() -> {
                if (renderer != null) {
                    renderer.repaint();
                }
            });
            
            try {
                Thread.sleep(16);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    private void tick(float tickDelta) {
        if (networkConnection != null && networkConnection.isConnected()) {
            networkConnection.processPackets();
            
            clientTick++;
            networkConnection.sendPacket(new PlayerInputStateNDto(
                clientTick,
                wPressed,
                sPressed,
                aPressed,
                dPressed,
                spacePressed,
                false,
                playerYaw,
                playerPitch
            ));
        }
    }
    
    public void handleChunkData(int chunkX, int chunkZ, int[][][] blockStates) {
        DebugChunk chunk = world.getChunk(chunkX, chunkZ);
        if (chunk == null) {
            chunk = new DebugChunk(chunkX, chunkZ);
            world.putChunk(chunkX, chunkZ, chunk);
        }
        chunk.setBlockStates(blockStates);
    }
    
    public void handleChunkUnload(int chunkX, int chunkZ) {
        world.removeChunk(chunkX, chunkZ);
    }
    
    public void handleBlockUpdate(int x, int y, int z, int blockId) {
        int chunkX = (int) Math.floor((float) x / DebugChunk.CHUNK_SIZE);
        int chunkZ = (int) Math.floor((float) z / DebugChunk.CHUNK_SIZE);
        
        DebugChunk chunk = world.getChunk(chunkX, chunkZ);
        if (chunk != null) {
            int localX = x - chunkX * DebugChunk.CHUNK_SIZE;
            int localZ = z - chunkZ * DebugChunk.CHUNK_SIZE;
            chunk.setBlockState(localX, y, localZ, blockId);
        }
    }
    
    public void setPlayerPosition(double x, double y, double z, float yaw, float pitch) {
        this.playerX = x;
        this.playerY = y;
        this.playerZ = z;
        this.playerYaw = yaw;
        this.playerPitch = pitch;
    }
    
    public void updatePlayerPosition(double x, double y, double z, float yaw, float pitch) {
        this.playerX = x;
        this.playerY = y;
        this.playerZ = z;
        this.playerYaw = yaw;
        this.playerPitch = pitch;
    }
    
    public void onKeyPressed(int keyCode) {
        if (keyCode == KeyEvent.VK_W) {
            wPressed = true;
        }
        if (keyCode == KeyEvent.VK_S) {
            sPressed = true;
        }
        if (keyCode == KeyEvent.VK_A) {
            aPressed = true;
        }
        if (keyCode == KeyEvent.VK_D) {
            dPressed = true;
        }
        if (keyCode == KeyEvent.VK_SPACE) {
            spacePressed = true;
        }
    }
    
    public void onKeyReleased(int keyCode) {
        if (keyCode == KeyEvent.VK_W) {
            wPressed = false;
        }
        if (keyCode == KeyEvent.VK_S) {
            sPressed = false;
        }
        if (keyCode == KeyEvent.VK_A) {
            aPressed = false;
        }
        if (keyCode == KeyEvent.VK_D) {
            dPressed = false;
        }
        if (keyCode == KeyEvent.VK_SPACE) {
            spacePressed = false;
        }
    }
    
    public void stop() {
        running = false;
        if (networkConnection != null) {
            networkConnection.disconnect();
        }
        if (frame != null) {
            frame.dispose();
        }
    }
}

