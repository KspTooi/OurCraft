package com.ksptool.ourcraft.sharedcore.utils.viewport;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Set;

import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.ksptool.ourcraft.sharedcore.utils.position.ChunkPos;
import lombok.Getter;
import lombok.Setter;

/**
 * 区块视口可视化工具
 * 用于测试和可视化ChunkPos位置及视口范围
 */
public class ChunkViewPortVisualizer extends JFrame {

    private static final int CELL_SIZE = 15;
    private static final int GRID_SIZE = 50;
    private static final int WINDOW_WIDTH = 600;
    private static final int WINDOW_HEIGHT = 600;
    
    @Getter
    @Setter
    private ChunkViewPort viewPort;
    
    private ChunkGridPanel gridPanel;
    private JSpinner centerXSpinner;
    private JSpinner centerZSpinner;
    private JSpinner viewDistanceSpinner;
    private JComboBox<String> modeComboBox;
    private JLabel infoLabel;

    public ChunkViewPortVisualizer() {
        setTitle("区块视口可视化工具");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        
        viewPort = ChunkViewPort.of(ChunkPos.of(0, 0, 0), 3);
        initComponents();
        layoutComponents();
        setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        setLocationRelativeTo(null);
    }

    private void initComponents() {
        gridPanel = new ChunkGridPanel();
        gridPanel.setPreferredSize(new Dimension(CELL_SIZE * GRID_SIZE, CELL_SIZE * GRID_SIZE));
        
        centerXSpinner = new JSpinner(new SpinnerNumberModel(0, -100, 100, 1));
        centerZSpinner = new JSpinner(new SpinnerNumberModel(0, -100, 100, 1));
        viewDistanceSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 320, 1));
        modeComboBox = new JComboBox<>(new String[]{"矩形", "圆形"});
        modeComboBox.setSelectedIndex(0);
        
        infoLabel = new JLabel("中心: (0, 0), 视口距离: 3");
        
        ChangeListener updateListener = (ChangeEvent e) -> updateViewPort();
        centerXSpinner.addChangeListener(updateListener);
        centerZSpinner.addChangeListener(updateListener);
        viewDistanceSpinner.addChangeListener(updateListener);
        modeComboBox.addActionListener(e -> updateViewPort());
        
        gridPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int offsetX = gridPanel.getWidth() / 2;
                int offsetZ = gridPanel.getHeight() / 2;
                int gridX = (e.getX() - offsetX) / CELL_SIZE;
                int gridZ = (e.getY() - offsetZ) / CELL_SIZE;
                centerXSpinner.setValue(gridX);
                centerZSpinner.setValue(gridZ);
            }
        });
    }

    private void layoutComponents() {
        JPanel controlPanel = new JPanel();
        controlPanel.add(new JLabel("中心X:"));
        controlPanel.add(centerXSpinner);
        controlPanel.add(new JLabel("中心Z:"));
        controlPanel.add(centerZSpinner);
        controlPanel.add(new JLabel("视口距离:"));
        controlPanel.add(viewDistanceSpinner);
        controlPanel.add(new JLabel("模式:"));
        controlPanel.add(modeComboBox);
        
        add(controlPanel, BorderLayout.NORTH);
        add(gridPanel, BorderLayout.CENTER);
        add(infoLabel, BorderLayout.SOUTH);
    }

    private void updateViewPort() {
        int centerX = (Integer) centerXSpinner.getValue();
        int centerZ = (Integer) centerZSpinner.getValue();
        int viewDistance = (Integer) viewDistanceSpinner.getValue();
        int mode = modeComboBox.getSelectedIndex();
        
        viewPort = ChunkViewPort.of(ChunkPos.of(centerX, 0, centerZ), viewDistance);
        viewPort.setMode(mode);
        String modeName = mode == 0 ? "矩形" : "圆形";
        infoLabel.setText(String.format("中心: (%d, %d), 视口距离: %d, 模式: %s, 区块数量: %d", 
            centerX, centerZ, viewDistance, modeName, viewPort.getChunkPosSet().size()));
        gridPanel.repaint();
    }

    private class ChunkGridPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            
            Set<ChunkPos> viewPortChunks = viewPort.getChunkPosSet();
            ChunkPos center = viewPort.getCenter();
            
            int offsetX = getWidth() / 2;
            int offsetZ = getHeight() / 2;
            
            for (int x = -GRID_SIZE / 2; x < GRID_SIZE / 2; x++) {
                for (int z = -GRID_SIZE / 2; z < GRID_SIZE / 2; z++) {
                    ChunkPos pos = ChunkPos.of(x, 0, z);
                    int screenX = offsetX + x * CELL_SIZE;
                    int screenZ = offsetZ + z * CELL_SIZE;
                    
                    if (viewPortChunks.contains(pos)) {
                        g2d.setColor(Color.GREEN);
                    } else {
                        g2d.setColor(Color.LIGHT_GRAY);
                    }
                    
                    g2d.fillRect(screenX, screenZ, CELL_SIZE - 1, CELL_SIZE - 1);
                    
                    if (pos.equals(center)) {
                        g2d.setColor(Color.RED);
                        g2d.fillOval(screenX + CELL_SIZE / 4, screenZ + CELL_SIZE / 4, 
                            CELL_SIZE / 2, CELL_SIZE / 2);
                    }
                    
                    g2d.setColor(Color.BLACK);
                    g2d.drawRect(screenX, screenZ, CELL_SIZE - 1, CELL_SIZE - 1);
                }
            }
            
            g2d.setColor(Color.BLUE);
            g2d.drawLine(offsetX, 0, offsetX, getHeight());
            g2d.drawLine(0, offsetZ, getWidth(), offsetZ);
        }
    }

    void main() {
        SwingUtilities.invokeLater(() -> {
            ChunkViewPortVisualizer visualizer = new ChunkViewPortVisualizer();
            visualizer.setVisible(true);
        });
    }
}

