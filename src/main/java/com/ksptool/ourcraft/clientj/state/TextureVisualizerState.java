package com.ksptool.ourcraft.clientj.state;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.MouseAxisTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Texture;
import com.ksptool.ourcraft.clientj.OurCraftClientJ;
import com.ksptool.ourcraft.clientj.commons.FontSize;
import com.ksptool.ourcraft.clientj.commons.RGBA;
import com.ksptool.ourcraft.clientj.service.StateService;
import com.ksptool.ourcraft.clientj.service.TextureService;
import com.ksptool.ourcraft.clientj.ui.GlowButton;
import lombok.extern.slf4j.Slf4j;

/**
 * 纹理图集可视化器，用于显示TextureService打包的最终纹理图集
 */
@Slf4j
public class TextureVisualizerState extends BaseAppState implements ActionListener, AnalogListener {

    private final OurCraftClientJ client;
    private final StateService stateService;

    private Node guiNode;
    private Geometry atlasGeometry;
    private Material atlasMaterial;
    private GlowButton backButton;

    // 缩放和拖拽相关
    private float scale = 1.0f;
    private float minScale = 0.1f;
    private float maxScale = 5.0f;
    private Vector3f dragOffset = new Vector3f();
    private boolean isDragging = false;
    private Vector2f lastMousePos = new Vector2f();

    public TextureVisualizerState(OurCraftClientJ client) {
        this.client = client;
        this.stateService = client.getStateService();
    }

    @Override
    protected void initialize(Application app) {
        guiNode = new Node("TextureServiceVisualizer");

        try {
            // 获取TextureService实例
            TextureService textureService = TextureService.getInstance();

            // 加载纹理图集
            textureService.loadAtlas();

            // 获取纹理图集
            Texture atlasTexture = textureService.getTexture();
            if (atlasTexture == null) {
                log.error("纹理图集加载失败，无法显示");
                return;
            }

            int atlasWidth = textureService.getAtlasWidth();
            int atlasHeight = textureService.getAtlasHeight();

            // 创建显示用的四边形，保持图集的宽高比
            float displayWidth = 800f;  // 显示宽度
            float displayHeight = (float) atlasHeight / atlasWidth * displayWidth;

            // 如果高度太大，调整大小
            if (displayHeight > 600f) {
                displayHeight = 600f;
                displayWidth = (float) atlasWidth / atlasHeight * displayHeight;
            }

            Mesh quad = new Quad(displayWidth, displayHeight);

            // 创建几何体
            atlasGeometry = new Geometry("AtlasDisplay", quad);

            // 创建材质
            atlasMaterial = new Material(client.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
            atlasMaterial.setTexture("ColorMap", atlasTexture);
            atlasMaterial.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);

            // 应用材质
            atlasGeometry.setMaterial(atlasMaterial);

            // 定位到屏幕中央
            float screenWidth = client.getCamera().getWidth();
            float screenHeight = client.getCamera().getHeight();

            atlasGeometry.setLocalTranslation(
                (screenWidth - displayWidth) / 2,
                (screenHeight - displayHeight) / 2,
                0
            );

            guiNode.attachChild(atlasGeometry);

            // 创建返回按钮
            backButton = new GlowButton("返回主菜单", FontSize.NORMAL);
            backButton.size(150, 40)
                    .normalColor(RGBA.of(100, 100, 100, 200))
                    .hoverColor(RGBA.of(130, 130, 130, 230))
                    .pressedColor(RGBA.of(80, 80, 80, 250))
                    .border(RGBA.of(150, 150, 150, 255), 1)
                    .onClick(this::onBackButtonClicked);

            // 定位返回按钮到左上角
            backButton.setLocalTranslation(20, client.getCamera().getHeight() - 60, 1);
            guiNode.attachChild(backButton);

            // 设置输入监听器
            setupInput();

            log.info("纹理图集可视化器初始化完成，图集大小: {}x{}", atlasWidth, atlasHeight);

        } catch (Exception e) {
            log.error("初始化纹理图集可视化器失败", e);
        }
    }

    @Override
    protected void onEnable() {
        client.getGuiNode().attachChild(guiNode);
        log.info("纹理图集可视化器已启用");
    }

    @Override
    protected void onDisable() {
        guiNode.removeFromParent();
        removeInput();
        log.info("纹理图集可视化器已禁用");
    }

    @Override
    protected void cleanup(Application app) {
        if (atlasGeometry != null) {
            atlasGeometry.removeFromParent();
        }
        guiNode.detachAllChildren();
    }

    @Override
    public void update(float tpf) {
        // 可以在这里添加动画或其他动态效果
    }

    /**
     * 设置输入监听器
     */
    private void setupInput() {
        // 鼠标滚轮缩放
        client.getInputManager().addMapping("MouseWheelUp", new MouseAxisTrigger(MouseInput.AXIS_WHEEL, false));
        client.getInputManager().addMapping("MouseWheelDown", new MouseAxisTrigger(MouseInput.AXIS_WHEEL, true));

        // 鼠标拖拽
        client.getInputManager().addMapping("MouseDrag", new MouseButtonTrigger(MouseInput.BUTTON_LEFT));

        client.getInputManager().addListener(this, "MouseWheelUp", "MouseWheelDown", "MouseDrag");
    }

    /**
     * 返回按钮点击事件
     */
    private void onBackButtonClicked() {
        stateService.toMain();
    }

    /**
     * 移除输入监听器
     */
    private void removeInput() {
        client.getInputManager().removeListener(this);
        client.getInputManager().deleteMapping("MouseWheelUp");
        client.getInputManager().deleteMapping("MouseWheelDown");
        client.getInputManager().deleteMapping("MouseDrag");
    }

    /**
     * 更新纹理图集的缩放和平移
     */
    private void updateAtlasTransform() {
        if (atlasGeometry != null) {
            // 获取原始尺寸
            TextureService textureService = TextureService.getInstance();
            int atlasWidth = textureService.getAtlasWidth();
            int atlasHeight = textureService.getAtlasHeight();

            float scaledWidth = atlasWidth * scale;
            float scaledHeight = atlasHeight * scale;

            // 更新Quad尺寸
            if (atlasGeometry.getMesh() instanceof Quad) {
                Quad quad = (Quad) atlasGeometry.getMesh();
                quad.updateGeometry(scaledWidth, scaledHeight);
            }

            // 计算居中位置加上拖拽偏移
            float screenWidth = client.getCamera().getWidth();
            float screenHeight = client.getCamera().getHeight();

            float centerX = (screenWidth - scaledWidth) / 2 + dragOffset.x;
            float centerY = (screenHeight - scaledHeight) / 2 + dragOffset.y;

            atlasGeometry.setLocalTranslation(centerX, centerY, 0);
        }
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        if ("MouseDrag".equals(name)) {
            isDragging = isPressed;
            if (isPressed) {
                // 开始拖拽时记录鼠标位置
                Vector2f mousePos = client.getInputManager().getCursorPosition();
                lastMousePos.set(mousePos);
            }
        }
    }

    @Override
    public void onAnalog(String name, float value, float tpf) {
        if ("MouseWheelUp".equals(name)) {
            // 放大
            scale = Math.min(scale * 1.1f, maxScale);
            updateAtlasTransform();
        } else if ("MouseWheelDown".equals(name)) {
            // 缩小
            scale = Math.max(scale * 0.9f, minScale);
            updateAtlasTransform();
        } else if ("MouseDrag".equals(name) && isDragging) {
            // 拖拽移动
            Vector2f currentMousePos = client.getInputManager().getCursorPosition();
            Vector2f delta = currentMousePos.subtract(lastMousePos);
            dragOffset.addLocal(delta.x, delta.y, 0);
            lastMousePos.set(currentMousePos);
            updateAtlasTransform();
        }
    }

    /**
     * 响应窗口大小变化
     */
    public void reshape(int w, int h) {
        // 更新返回按钮位置
        if (backButton != null) {
            backButton.setLocalTranslation(20, h - 60, 1);
        }

        // 重新居中纹理图集
        updateAtlasTransform();
    }
}
