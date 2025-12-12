package com.ksptool.ourcraft.clientj.state;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.texture.Texture;
import com.ksptool.ourcraft.clientj.OurCraftClientJ;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.component.SpringGridLayout;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MainMenuStateOld extends BaseAppState {

    private final OurCraftClientJ client;
    // 布局比例 (蓝色部分占屏幕高度的 35%)
    private final float BOTTOM_HEIGHT_PERCENT = 0.35f;
    private Node guiNode;
    // UI 容器
    private Container topContainer;
    private Container topPanel;
    private Container bottomPanel;

    public MainMenuStateOld(OurCraftClientJ client) {
        this.client = client;
    }

    @Override
    protected void initialize(Application app) {
        this.guiNode = ((SimpleApplication) app).getGuiNode();

        // 1. 初始化 Lemur (如果 Main 类里没初始化，这里必须加)
        // GuiGlobals.initialize(app);

        // 2. 构建 UI
        buildUI();

        // 3. 初始布局计算
        refreshLayout(app.getCamera().getWidth(), app.getCamera().getHeight());
    }

    // 优化后的构建代码
    private void buildUI() {
        // 1. 创建一个根容器，填满屏幕，使用 Y 轴布局（从上到下排列）
        // FillMode.Even 表示宽度会自动撑满
        topContainer = new Container(new SpringGridLayout(Axis.Y, Axis.X, FillMode.None, FillMode.Even));
        guiNode.attachChild(topContainer);

        // 2. 上半部分 (Top)
        topPanel = topContainer.addChild(new Container());
        topPanel.setBackground(new QuadBackgroundComponent(ColorRGBA.White)); // 默认白色

        // 3. 下半部分 (Bottom)
        bottomPanel = topContainer.addChild(new Container());
        bottomPanel.setBackground(new QuadBackgroundComponent(new ColorRGBA(0.2f, 0.25f, 0.85f, 0.85f)));

        // 下半部分的内容布局 (居中)
        bottomPanel.setLayout(new SpringGridLayout(Axis.Y, Axis.X, FillMode.None, FillMode.Even));

        // 链式添加组件，代码更少
        bottomPanel.addChild(new Label("Server: 127.0.0.1")).setTextHAlignment(HAlignment.Center);
        bottomPanel.addChild(new Label("Status: Online")).setTextHAlignment(HAlignment.Center);
        Button btn = bottomPanel.addChild(new Button("Connect"));
        btn.setTextHAlignment(HAlignment.Center);
        btn.addClickCommands(b -> System.out.println("Click"));

        // 触发一次布局计算
        refreshLayout(client.getCamera().getWidth(), client.getCamera().getHeight());
    }

    /**
     * 设置顶部背景
     *
     * @param imagePath 图片路径 (例如 "Textures/bg.png")，如果为 null 则显示纯白
     */
    public void setTopBackground(String imagePath) {
        if (topContainer == null) return;

        if (imagePath != null) {
            // 加载图片
            Texture tex = client.getAssetManager().loadTexture(imagePath);
            topContainer.setBackground(new QuadBackgroundComponent(tex));
        } else {
            // 默认白色
            topContainer.setBackground(new QuadBackgroundComponent(ColorRGBA.White));
        }
    }

    // 极其简化的布局刷新
    public void refreshLayout(int w, int h) {
        // 设置根容器大小
        topContainer.setPreferredSize(new Vector3f(w, h, 0));
        topContainer.setLocalTranslation(0, h, 0); // 只需要定位根容器

        // 设置子面板的高度 (Lemur 会自动把 top 放在上面，bottom 放在下面)
        topPanel.setPreferredSize(new Vector3f(w, h * 0.65f, 0));
        bottomPanel.setPreferredSize(new Vector3f(w, h * 0.35f, 0));
    }

    // JME 的回调：当窗口大小改变时触发
    public void reshape(int w, int h) {
        // 由于 BaseAppState 没有 reshape 方法，我们需要在 Main 类里转发，
        // 或者在这里简单地使用 SimpleUpdate 检测。
        // 但通常我们会在这里手动调用布局更新。
        refreshLayout(w, h);
    }

    @Override
    public void update(float tpf) {

    }

    @Override
    protected void onEnable() {
        if (guiNode != null) {
            guiNode.attachChild(topContainer);
        }
        client.getInputManager().setCursorVisible(true);
    }

    @Override
    protected void onDisable() {
        if (topContainer != null) topContainer.removeFromParent();
    }

    @Override
    protected void cleanup(Application app) {
        // 清理资源
    }
}