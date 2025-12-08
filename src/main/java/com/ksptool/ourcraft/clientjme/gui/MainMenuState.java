package com.ksptool.ourcraft.clientjme.gui;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.input.InputManager;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.scene.Node;
import com.ksptool.ourcraft.clientjme.OurCraftClientJme;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class MainMenuState extends BaseAppState {

    private Node guiNode;
    private BitmapText multiplayerButton;
    private BitmapText exitButton;
    private BitmapFont guiFont;
    
    private float multiplayerButtonY;
    private float exitButtonY;
    private float buttonHeight = 30;
    private float buttonSpacing = 50;
    
    private OurCraftClientJme app;

    @Override
    protected void initialize(Application app) {
        this.app = (OurCraftClientJme) app;
        this.guiNode = new Node("MainMenuGUI");
        
        // 加载默认字体
        guiFont = this.app.getAssetManager().loadFont("Interface/Fonts/Default.fnt");
        
        // 创建"多人游戏"按钮
        multiplayerButton = new BitmapText(guiFont);
        multiplayerButton.setSize(guiFont.getCharSet().getRenderedSize() * 2);
        multiplayerButton.setText("Multiplayer"); 
        multiplayerButton.setColor(ColorRGBA.White);
        
        // 创建"退出"按钮
        exitButton = new BitmapText(guiFont);
        exitButton.setSize(guiFont.getCharSet().getRenderedSize() * 2); 
        exitButton.setText("Exit"); 
        exitButton.setColor(ColorRGBA.White);
        
        // 计算按钮位置（居中）
        float screenWidth = this.app.getCamera().getWidth();
        float screenHeight = this.app.getCamera().getHeight();
        
        float multiplayerButtonWidth = multiplayerButton.getLineWidth();
        float exitButtonWidth = exitButton.getLineWidth();
        
        multiplayerButtonY = screenHeight / 2 + buttonSpacing / 2;
        exitButtonY = screenHeight / 2 - buttonSpacing / 2;
        
        multiplayerButton.setLocalTranslation(
            (screenWidth - multiplayerButtonWidth) / 2,
            multiplayerButtonY,
            0
        );
        
        exitButton.setLocalTranslation(
            (screenWidth - exitButtonWidth) / 2,
            exitButtonY,
            0
        );
        
        guiNode.attachChild(multiplayerButton);
        guiNode.attachChild(exitButton);
        
        log.info("主菜单初始化完成，按钮位置: Multiplayer Y={}, Exit Y={}", multiplayerButtonY, exitButtonY);
        log.info("按钮宽度: Multiplayer={}, Exit={}", multiplayerButtonWidth, exitButtonWidth);
        
        // 注册鼠标点击监听
        InputManager inputManager = this.app.getInputManager();
        inputManager.addMapping("MouseClick", new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addListener(clickListener, "MouseClick");
    }

    @Override
    protected void cleanup(Application app) {
        InputManager inputManager = app.getInputManager();
        inputManager.removeListener(clickListener);
        inputManager.deleteMapping("MouseClick");
        
        if (guiNode != null) {
            guiNode.removeFromParent();
        }
    }

    @Override
    protected void onEnable() {
        if (guiNode != null && app != null) {
            app.getGuiNode().attachChild(guiNode);
            
            // 确保显示鼠标光标并禁用飞翔相机
            app.getInputManager().setCursorVisible(true);
            app.getFlyByCamera().setEnabled(false);
            
            log.info("主菜单GUI已附加到场景，子节点数量: {}", guiNode.getChildren().size());
        }
    }

    @Override
    protected void onDisable() {
        if (guiNode != null) {
            guiNode.removeFromParent();
        }
    }

    private final ActionListener clickListener = (name, isPressed, tpf) -> {
        if (!isPressed) {
            return;
        }
        
        InputManager inputManager = app.getInputManager();
        Vector2f clickPos = inputManager.getCursorPosition();
        
        log.info("鼠标点击位置: ({}, {})", clickPos.x, clickPos.y);
        
        // 检查是否点击了"多人游戏"按钮
        float multiplayerButtonWidth = multiplayerButton.getLineWidth();
        float multiplayerButtonX = (app.getCamera().getWidth() - multiplayerButtonWidth) / 2;
        
        log.info("多人游戏按钮区域: X[{}, {}], Y[{}, {}]", 
            multiplayerButtonX, multiplayerButtonX + multiplayerButtonWidth,
            multiplayerButtonY - buttonHeight, multiplayerButtonY);
        
        if (clickPos.x >= multiplayerButtonX && 
            clickPos.x <= multiplayerButtonX + multiplayerButtonWidth &&
            clickPos.y >= multiplayerButtonY - buttonHeight &&
            clickPos.y <= multiplayerButtonY) {
            
            log.info("点击了多人游戏按钮，开始连接服务器...");
            connectToServer();
            return;
        }
        
        // 检查是否点击了"退出"按钮
        float exitButtonWidth = exitButton.getLineWidth();
        float exitButtonX = (app.getCamera().getWidth() - exitButtonWidth) / 2;
        
        log.info("退出按钮区域: X[{}, {}], Y[{}, {}]", 
            exitButtonX, exitButtonX + exitButtonWidth,
            exitButtonY - buttonHeight, exitButtonY);
        
        if (clickPos.x >= exitButtonX && 
            clickPos.x <= exitButtonX + exitButtonWidth &&
            clickPos.y >= exitButtonY - buttonHeight &&
            clickPos.y <= exitButtonY) {
            
            log.info("点击了退出按钮");
            app.stop();
            return;
        }
    };

    private void connectToServer() {
        try {
            app.connectToServer("127.0.0.1", 25564);
        } catch (Exception e) {
            log.error("连接服务器失败", e);
        }
    }
}

