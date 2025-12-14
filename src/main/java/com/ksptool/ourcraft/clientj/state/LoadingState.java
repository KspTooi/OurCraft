package com.ksptool.ourcraft.clientj.state;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.math.ColorRGBA;
import com.ksptool.ourcraft.clientj.OurCraftClientJ;
import com.ksptool.ourcraft.clientj.service.ClientNetworkService;
import com.ksptool.ourcraft.clientj.service.GlobalFontService;
import com.ksptool.ourcraft.clientj.service.ClientStateService;
import com.ksptool.ourcraft.clientj.commons.FontSize;
import com.ksptool.ourcraft.clientj.commons.RGBA;
import com.ksptool.ourcraft.clientj.commons.event.ProcessSwitchEvent;
import com.ksptool.ourcraft.clientj.commons.event.SessionCloseEvent;
import com.ksptool.ourcraft.clientj.commons.event.SessionUpdateEvent;
import com.ksptool.ourcraft.clientj.network.ClientNetworkSession;
import com.ksptool.ourcraft.clientj.ui.GlowBody;
import com.ksptool.ourcraft.clientj.ui.GlowDiv;
import com.ksptool.ourcraft.clientj.ui.TTFLabel;
import com.ksptool.ourcraft.clientj.ui.GlowButton;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * 加载状态
 * 用于连接服务器、进程切换、计算落地区块MESH
 */
@Slf4j
public class LoadingState extends BaseAppState {

    private final OurCraftClientJ client;

    private GlowBody body;
    private GlowDiv container;
    private TTFLabel titleLabel;
    private GlowButton cancelButton;

    //状态服务
    private final ClientStateService css;

    //网络服务
    private final ClientNetworkService cns;

    private volatile String currentStatus = "正在连接服务器...";

    //连接目标
    @Setter
    private String host = "127.0.0.1";
    @Setter
    private int port = 25564;

    public LoadingState(OurCraftClientJ client) {
        this.client = client;
        this.css = client.getClientStateService();
        this.cns = client.getClientNetworkService();
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
        cns.connect(host, port);

        //取消所有事件订阅
        client.getCes().unsubscribeAll();

        //订阅本阶段需要的事件
        client.getCes().subscribe(ProcessSwitchEvent.class, this::onProcessSwitch);
        client.getCes().subscribe(SessionCloseEvent.class, this::onSessionClosed);
        client.getCes().subscribe(SessionUpdateEvent.class, this::onSessionUpdate);

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
        //取消所有事件订阅
        client.getCes().unsubscribeAll();
        //断开连接
        cns.disconnect();
        body = null;
        container = null;
        titleLabel = null;
        cancelButton = null;
        log.info("清理加载状态资源完成");
    }

    @Override
    public void update(float tpf) {

        //处理事件
        client.getCes().action(tpf, null);

    }

    public void onProcessSwitch(ProcessSwitchEvent event) {
        log.info("服务器要求进程切换: {}", event.getWorldName());
    }

    /**
     * 服务器连接失败事件
     * @param event 事件
     */
    public void onSessionClosed(SessionCloseEvent event) {
        log.info("服务器连接失败: {}", event.getReason());
        css.toMain();
    }

    /**
     * 服务器连接状态更新事件
     * @param event 事件
     */
    public void onSessionUpdate(SessionUpdateEvent event) {
        updateStatus(event.getText());
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
        css.toMain();
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