package com.ksptool.ourcraft.clientj.state;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.math.ColorRGBA;
import com.ksptool.ourcraft.clientj.OurCraftClientJ;
import com.ksptool.ourcraft.clientj.service.GlobalFontService;
import com.ksptool.ourcraft.clientj.commons.FontSize;
import com.ksptool.ourcraft.clientj.commons.RGBA;
import com.ksptool.ourcraft.clientj.ui.GlowBody;
import com.ksptool.ourcraft.clientj.ui.GlowButton;
import com.ksptool.ourcraft.clientj.ui.GlowDiv;
import com.ksptool.ourcraft.clientj.ui.TTFLabel;
import com.ksptool.ourcraft.clientj.state.LoadingState;
import com.ksptool.ourcraft.clientj.service.StateService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MainMenuState extends BaseAppState {

    private final OurCraftClientJ client;

    private StateService stateService;

    private GlowBody body;
    private GlowDiv menuContainer;
    private TTFLabel titleLabel;
    private GlowButton multiplayerButton;
    private GlowButton exitButton;


    public MainMenuState(OurCraftClientJ client) {
        this.client = client;
        this.stateService = client.getStateService();
    }

    @Override
    protected void initialize(Application app) {
        // 【关键修复】预加载所有文字，防止动态图集重排导致渲染问题
        GlobalFontService.preloadText("OurCraft", FontSize.XLARGE);
        GlobalFontService.preloadText("多人模式退出", FontSize.LARGE);
        GlobalFontService.preloadText("纹理查看器", FontSize.LARGE);
        GlobalFontService.preloadText("退出", FontSize.LARGE);

        // 创建主体背景
        body = new GlowBody(app);
        // 设置深色背景，增加游戏氛围
        body.bg(new ColorRGBA(0.1f, 0.15f, 0.2f, 1f));
        body.layoutNull();

        // 创建菜单容器
        menuContainer = new GlowDiv();
        menuContainer.size(500, 400)
                .bg(RGBA.of(20, 30, 40, 220))
                .border(RGBA.of(100, 150, 200, 255), 1);

        body.attachChild(menuContainer);
        menuContainer.centerInParent(true);

        // 使用绝对定位布局，手动计算位置
        menuContainer.layoutNull();

        // 创建标题
        titleLabel = new TTFLabel("OurCraft", FontSize.XLARGE, RGBA.of(100, 200, 255, 255));
        titleLabel.textAlignCenter();
        titleLabel.setPreferredSize(titleLabel.getPreferredSize());
        menuContainer.attachChild(titleLabel);

        // 手动定位标题到顶部
        titleLabel.setLocalTranslation(250 - titleLabel.getPreferredSize().x / 2, -40, 1);

        // 创建"多人模式"按钮
        multiplayerButton = new GlowButton("多人模式", FontSize.LARGE);
        multiplayerButton.size(440, 70)
                .normalColor(RGBA.of(50, 100, 150, 200))
                .hoverColor(RGBA.of(70, 130, 190, 230))
                .pressedColor(RGBA.of(30, 80, 130, 250))
                .border(RGBA.of(100, 150, 200, 255), 1)
                .onClick(this::onMultiplayerClicked);
        menuContainer.attachChild(multiplayerButton);

        // 定位多人模式按钮
        multiplayerButton.setLocalTranslation(30, -120, 1);

        // 创建"纹理可视化器"按钮（调试用）
        GlowButton textureVisualizerButton = new GlowButton("纹理查看器", FontSize.LARGE);
        textureVisualizerButton.size(440, 70)
                .normalColor(RGBA.of(100, 100, 100, 200))
                .hoverColor(RGBA.of(130, 130, 130, 230))
                .pressedColor(RGBA.of(80, 80, 80, 250))
                .border(RGBA.of(150, 150, 150, 255), 1)
                .onClick(this::onTextureVisualizerClicked);
        menuContainer.attachChild(textureVisualizerButton);

        // 定位纹理可视化器按钮
        textureVisualizerButton.setLocalTranslation(30, -200, 1);

        // 创建"退出"按钮
        exitButton = new GlowButton("退出", FontSize.LARGE);
        exitButton.size(440, 70)
                .normalColor(RGBA.of(150, 50, 50, 200))
                .hoverColor(RGBA.of(190, 70, 70, 230))
                .pressedColor(RGBA.of(130, 30, 30, 250))
                .border(RGBA.of(200, 100, 100, 255), 1)
                .onClick(this::onExitClicked);
        menuContainer.attachChild(exitButton);

        // 定位退出按钮
        exitButton.setLocalTranslation(30, -280, 1);

    }

    /**
     * 多人模式按钮点击事件
     */
    private void onMultiplayerClicked() {
        // 启用加载状态
        stateService.joinServer();
    }

    /**
     * 纹理可视化器按钮点击事件
     */
    private void onTextureVisualizerClicked() {
        stateService.showArrayTextureVisualizer();
    }

    /**
     * 退出按钮点击事件
     */
    private void onExitClicked() {
        client.stop();
    }

    @Override
    public void update(float tpf) {
        // 可以在这里添加动画效果
    }

    @Override
    protected void onEnable() {
        client.getGuiNode().attachChild(body);
    }

    @Override
    protected void onDisable() {
        client.getGuiNode().detachChild(body);
    }

    @Override
    protected void cleanup(Application app) {
    }

    /**
     * 响应窗口大小变化
     */
    public void reshape(int w, int h) {
        if (body != null) {
            body.resizeAndReposition();

            if (menuContainer != null) {
                menuContainer.centerInParent(true);
            }
        }
    }
}