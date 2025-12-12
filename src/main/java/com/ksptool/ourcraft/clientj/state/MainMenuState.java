package com.ksptool.ourcraft.clientj.state;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.math.ColorRGBA;
import com.ksptool.ourcraft.clientj.OurCraftClientJ;
import com.ksptool.ourcraft.clientj.commons.RGBA;
import com.ksptool.ourcraft.clientj.ui.GlowBody;
import com.ksptool.ourcraft.clientj.ui.GlowDiv;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MainMenuState extends BaseAppState {

    private final OurCraftClientJ client;

    private GlowBody body;
    private GlowDiv div;

    public MainMenuState(OurCraftClientJ client) {
        this.client = client;
    }

    @Override
    protected void initialize(Application app) {
        body = new GlowBody(app);
        body.bg(new ColorRGBA(1F, 1F, 1F, 1));
        body.layoutNull();

        div = new GlowDiv();
        div.size(320, 320)
                .bg(RGBA.of(0, 0, 0, 128))
                .border(RGBA.of(0, 0, 0, 64), 1)
        ;

        body.attachChild(div);
        div.centerInParent(true);

        var btn = new GlowDiv();
        btn.size(300, 200)
                .bg(RGBA.of(0, 255, 0, 128))
                .border(RGBA.of(0, 0, 0, 64), 1);


        div.layoutNull();
        div.attachChild(btn);
        btn.centerInParent(true);
        //centerDiv();
    }

    @Override
    public void update(float tpf) {

    }

    @Override
    protected void onEnable() {
        client.getGuiNode().attachChild(body);
    }

    @Override
    protected void onDisable() {

    }

    @Override
    protected void cleanup(Application app) {

    }

    public void reshape(int w, int h) {
        body.resizeAndReposition();
        //centerDiv();
    }

    /*private void centerDiv() {
        if (div == null || body == null) return;

        // 获取父容器（屏幕）尺寸
        // 建议直接用 body 的尺寸，比调用 cam 更符合 UI 逻辑
        Vector3f parentSize = body.getPreferredSize();
        float pW = parentSize.x;
        float pH = parentSize.y;

        // 获取子容器尺寸
        Vector3f childSize = div.getPreferredSize();
        float cW = childSize.x;
        float cH = childSize.y;

        // 计算坐标
        // X轴：(父宽 - 子宽) / 2
        float x = (pW - cW) / 2;

        // Y轴：-(父高 - 子高) / 2
        // 注意：因为是从左上角向下排，所以 Y 应该是负值
        float y = -(pH - cH) / 2;

        // 设置坐标 (Z轴设为1或更高，确保在背景之上)
        div.setLocalTranslation(x, y, 1);
    }*/

}