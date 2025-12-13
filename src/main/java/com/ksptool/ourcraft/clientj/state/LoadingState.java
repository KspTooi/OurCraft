package com.ksptool.ourcraft.clientj.state;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.math.ColorRGBA;
import com.ksptool.ourcraft.clientj.OurCraftClientJ;
import com.ksptool.ourcraft.clientj.service.GlobalFontService;
import com.ksptool.ourcraft.clientj.service.StateService;
import com.ksptool.ourcraft.clientj.commons.FontSize;
import com.ksptool.ourcraft.clientj.commons.RGBA;
import com.ksptool.ourcraft.clientj.ui.GlowBody;
import com.ksptool.ourcraft.clientj.ui.GlowDiv;
import com.ksptool.ourcraft.clientj.ui.TTFLabel;
import com.ksptool.ourcraft.clientj.ui.GlowButton;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * 加载状态，用于显示连接服务器和初始化游戏世界的进度
 */
@Slf4j
public class LoadingState extends BaseAppState {

    private final OurCraftClientJ client;

    private GlowBody body;
    private GlowDiv container;
    private TTFLabel titleLabel;
    private GlowButton cancelButton;
    private StateService stateService;

    private String currentStatus = "正在连接服务器...";

    public LoadingState(OurCraftClientJ client) {
        this.client = client;
        this.stateService = client.getStateService();
    }

    @Override
    protected void initialize(Application app) {
        GlobalFontService.preloadText("正在连接服务器...", FontSize.LARGE);
        GlobalFontService.preloadText("取消", FontSize.NORMAL);

        body = new GlowBody(app);
        body.bg(new ColorRGBA(0.05f, 0.1f, 0.15f, 1f));
        body.layoutNull();

        container = new GlowDiv();
        container.size(400, 200)
                .bg(RGBA.of(15, 25, 35, 240))
                .border(RGBA.of(80, 120, 160, 255), 1);
        body.attachChild(container);
        container.centerInParent(true);
        container.layoutNull();

        titleLabel = new TTFLabel("正在连接服务器...", FontSize.LARGE, RGBA.of(100, 200, 255, 255));
        titleLabel.textAlignCenter();
        titleLabel.setPreferredSize(titleLabel.getPreferredSize());
        container.attachChild(titleLabel);
        titleLabel.setLocalTranslation(200 - titleLabel.getPreferredSize().x / 2, -50, 1);

        // 创建取消按钮
        cancelButton = new GlowButton("取消", FontSize.NORMAL);
        cancelButton.size(120, 40)
                .normalColor(RGBA.of(150, 50, 50, 200))
                .hoverColor(RGBA.of(190, 70, 70, 230))
                .pressedColor(RGBA.of(130, 30, 30, 250))
                .border(RGBA.of(200, 100, 100, 255), 1)
                .onClick(this::onCancelClicked);
        container.attachChild(cancelButton);
        cancelButton.setLocalTranslation(200 - 120 / 2, -140, 1);
    }

    @Override
    protected void onEnable() {
        client.getGuiNode().attachChild(body);
        log.info("进入加载状态");
    }

    @Override
    protected void onDisable() {
        client.getGuiNode().detachChild(body);
        log.info("离开加载状态");
    }

    @Override
    protected void cleanup(Application app) {
        // 清理资源
    }

    @Override
    public void update(float tpf) {
        // 可以在这里添加动画效果
    }

    /**
     * 更新加载状态文本
     * @param status 状态文本
     */
    public void updateStatus(String status) {
        if (StringUtils.isNotBlank(status)) {
            client.enqueue(() -> {
                GlobalFontService.preloadText(status, FontSize.LARGE);
                this.currentStatus = status;
                titleLabel.setText(status);
                titleLabel.setPreferredSize(titleLabel.getPreferredSize());
                titleLabel.setLocalTranslation(200 - titleLabel.getPreferredSize().x / 2, -50, 1);
            });
        }
    }

    /**
     * 取消按钮点击事件
     */
    private void onCancelClicked() {
        stateService.toMain();
    }

    /**
     * 响应窗口大小变化
     */
    public void reshape(int w, int h) {
        if (body != null) {
            body.resizeAndReposition();
            if (container != null) {
                container.centerInParent(true);
            }
        }
    }
}