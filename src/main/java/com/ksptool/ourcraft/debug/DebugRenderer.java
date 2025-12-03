package com.ksptool.ourcraft.debug;

import com.ksptool.ourcraft.sharedcore.GlobalPalette;
import com.ksptool.ourcraft.sharedcore.StdRegName;
import com.ksptool.ourcraft.sharedcore.world.BlockState;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.HashMap;
import java.util.Map;

/**
 * 调试客户端2D渲染器
 */
public class DebugRenderer extends JPanel implements KeyListener {
    
    private static final int BLOCK_SIZE = 8;
    private static final int VIEW_RANGE = 20;
    
    private DebugClient debugClient;
    private final Map<String, Color> blockColors = new HashMap<>();
    
    public DebugRenderer(DebugClient debugClient) {
        this.debugClient = debugClient;
        setPreferredSize(new Dimension(800, 800));
        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(this);
        
        initializeBlockColors();
    }
    
    private void initializeBlockColors() {
        blockColors.put("mycraft:air", new Color(0, 0, 0, 0));
        blockColors.put("mycraft:grass_block", new Color(124, 252, 0));
        blockColors.put("mycraft:dirt", new Color(139, 69, 19));
        blockColors.put("mycraft:stone", new Color(128, 128, 128));
        blockColors.put("mycraft:wood", new Color(139, 90, 43));
        blockColors.put("mycraft:leaves", new Color(34, 139, 34));
        blockColors.put("mycraft:water", new Color(0, 119, 190));
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        if (debugClient == null) {
            return;
        }
        
        double playerX = debugClient.getPlayerX();
        double playerY = debugClient.getPlayerY();
        double playerZ = debugClient.getPlayerZ();
        
        int centerX = getWidth() / 2;
        int centerZ = getHeight() / 2;
        
        int startX = (int) Math.floor(playerX) - VIEW_RANGE;
        int endX = (int) Math.floor(playerX) + VIEW_RANGE;
        int startZ = (int) Math.floor(playerZ) - VIEW_RANGE;
        int endZ = (int) Math.floor(playerZ) + VIEW_RANGE;
        
        GlobalPalette palette = GlobalPalette.getInstance();
        
        for (int x = startX; x <= endX; x++) {
            for (int z = startZ; z <= endZ; z++) {
                int topY = debugClient.getWorld().getTopBlockY(x, z);
                int stateId = debugClient.getWorld().getBlockState(x, topY, z);
                if (stateId == 0) {
                    continue;
                }
                
                BlockState state = palette.getState(stateId);
                StdRegName blockName = state.getSharedBlock().getStdRegName();
                Color color = blockColors.getOrDefault(blockName.getValue(), Color.WHITE);
                
                int screenX = centerX + (x - (int) Math.floor(playerX)) * BLOCK_SIZE;
                int screenZ = centerZ + (z - (int) Math.floor(playerZ)) * BLOCK_SIZE;
                
                if (screenX < -BLOCK_SIZE || screenX > getWidth() + BLOCK_SIZE ||
                    screenZ < -BLOCK_SIZE || screenZ > getHeight() + BLOCK_SIZE) {
                    continue;
                }
                
                g2d.setColor(color);
                g2d.fillRect(screenX, screenZ, BLOCK_SIZE, BLOCK_SIZE);
                
                int heightDiff = topY - (int) Math.floor(playerY);
                g2d.setColor(Color.WHITE);
                String text = String.valueOf(heightDiff);
                FontMetrics fm = g2d.getFontMetrics();
                int textWidth = fm.stringWidth(text);
                int textHeight = fm.getHeight();
                g2d.drawString(text, screenX + (BLOCK_SIZE - textWidth) / 2, 
                              screenZ + BLOCK_SIZE / 2 + textHeight / 4);
            }
        }
        
        g2d.setColor(Color.RED);
        g2d.fillOval(centerX - 4, centerZ - 4, 8, 8);
        
        g2d.setColor(Color.WHITE);
        g2d.drawString(String.format("位置: (%.1f, %.1f, %.1f)", playerX, playerY, playerZ), 10, 20);
    }
    
    @Override
    public void keyTyped(KeyEvent e) {
    }
    
    @Override
    public void keyPressed(KeyEvent e) {
        if (debugClient != null) {
            debugClient.onKeyPressed(e.getKeyCode());
        }
    }
    
    @Override
    public void keyReleased(KeyEvent e) {
        if (debugClient != null) {
            debugClient.onKeyReleased(e.getKeyCode());
        }
    }
}

