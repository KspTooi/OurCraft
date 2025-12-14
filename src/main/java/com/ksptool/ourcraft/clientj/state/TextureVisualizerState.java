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
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.debug.WireBox;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Texture;
import com.ksptool.ourcraft.clientj.OurCraftClientJ;
import com.ksptool.ourcraft.clientj.commons.FontSize;
import com.ksptool.ourcraft.clientj.commons.RGBA;
import com.ksptool.ourcraft.clientj.service.ClientStateService;
import com.ksptool.ourcraft.clientj.service.TextureService;
import com.ksptool.ourcraft.clientj.ui.GlowButton;
import com.ksptool.ourcraft.clientj.ui.TTFLabel;
import lombok.extern.slf4j.Slf4j;

/**
 * 纹理图集可视化器，用于显示TextureService打包的最终纹理图集
 */
@Slf4j
public class TextureVisualizerState extends BaseAppState implements ActionListener, AnalogListener {

    private final OurCraftClientJ client;
    private final ClientStateService clientStateService;

    private Node guiNode;
    private Geometry atlasGeometry;
    private Material atlasMaterial;
    private Geometry borderGeometry;
    private Material borderMaterial;
    private TTFLabel sizeLabel;
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
        this.clientStateService = client.getClientStateService();
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

            // 创建边框
            createBorder(atlasWidth, atlasHeight);

            // 创建大小显示标签
            createSizeLabel(atlasWidth, atlasHeight);

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

            // 初始化边框和标签位置
            updateBorderAndLabel();

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
        //重置缩放和拖拽状态
        scale = 1.0f;
        dragOffset.set(0, 0, 0);
        isDragging = false;
        lastMousePos.set(0, 0);
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
        clientStateService.toMain();
    }

    /**
     * 以鼠标位置为中心进行缩放
     * @param zoomFactor 缩放因子，大于1表示放大，小于1表示缩小
     */
    private void zoomAtMouse(float zoomFactor) {
        if (atlasGeometry == null) {
            return;
        }

        // 获取鼠标位置
        Vector2f mousePos = client.getInputManager().getCursorPosition();

        // 获取纹理图集的当前位置
        Vector3f atlasPos = atlasGeometry.getLocalTranslation();

        // 获取纹理图集的当前尺寸（已缩放后的）
        TextureService textureService = TextureService.getInstance();
        int atlasWidth = textureService.getAtlasWidth();
        int atlasHeight = textureService.getAtlasHeight();
        float currentWidth = atlasWidth * scale;
        float currentHeight = atlasHeight * scale;

        // 计算鼠标在纹理图集坐标系中的相对位置 (0-1)
        float relativeX = (mousePos.x - atlasPos.x) / currentWidth;
        float relativeY = (mousePos.y - atlasPos.y) / currentHeight;

        // 确保相对位置在有效范围内
        relativeX = Math.max(0, Math.min(1, relativeX));
        relativeY = Math.max(0, Math.min(1, relativeY));

        // 计算新的缩放比例
        float newScale = Math.max(minScale, Math.min(maxScale, scale * zoomFactor));

        // 如果缩放比例没有变化，提前返回
        if (newScale == scale) {
            return;
        }

        // 计算新的尺寸
        float newWidth = atlasWidth * newScale;
        float newHeight = atlasHeight * newScale;

        // 计算新的位置，使得鼠标指向的相对位置保持不变
        // 新位置 = 鼠标位置 - (相对位置 * 新尺寸)
        float newAtlasX = mousePos.x - relativeX * newWidth;
        float newAtlasY = mousePos.y - relativeY * newHeight;

        // 计算居中位置的偏移
        float screenWidth = client.getCamera().getWidth();
        float screenHeight = client.getCamera().getHeight();
        float centerOffsetX = (screenWidth - newWidth) / 2;
        float centerOffsetY = (screenHeight - newHeight) / 2;

        // 更新拖拽偏移
        dragOffset.x = newAtlasX - centerOffsetX;
        dragOffset.y = newAtlasY - centerOffsetY;

        // 应用新的缩放比例
        scale = newScale;

        // 更新显示
        updateAtlasTransform();
    }

    /**
     * 创建纹理图集边框
     */
    private void createBorder(int atlasWidth, int atlasHeight) {
        // 创建线框边框
        WireBox wireBox = new WireBox(atlasWidth / 2f, atlasHeight / 2f, 0.1f);
        borderGeometry = new Geometry("AtlasBorder", wireBox);

        // 创建边框材质
        borderMaterial = new Material(client.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        borderMaterial.setColor("Color", ColorRGBA.Green);
        borderMaterial.getAdditionalRenderState().setWireframe(true);

        borderGeometry.setMaterial(borderMaterial);
        guiNode.attachChild(borderGeometry);
    }

    /**
     * 创建大小显示标签
     */
    private void createSizeLabel(int atlasWidth, int atlasHeight) {
        sizeLabel = new TTFLabel(atlasWidth + " x " + atlasHeight, FontSize.SMALL, RGBA.of(200, 200, 200, 255));
        sizeLabel.textAlignCenter();
        sizeLabel.setPreferredSize(sizeLabel.getPreferredSize());
        guiNode.attachChild(sizeLabel);
    }

    /**
     * 更新边框和大小标签的位置和大小
     */
    private void updateBorderAndLabel() {
        if (atlasGeometry == null || borderGeometry == null || sizeLabel == null) {
            return;
        }

        TextureService textureService = TextureService.getInstance();
        int atlasWidth = textureService.getAtlasWidth();
        int atlasHeight = textureService.getAtlasHeight();

        // 获取纹理图集的位置
        Vector3f atlasPos = atlasGeometry.getLocalTranslation();
        float scaledWidth = atlasWidth * scale;
        float scaledHeight = atlasHeight * scale;

        // 更新边框位置和大小
        borderGeometry.setLocalTranslation(atlasPos.x + scaledWidth / 2, atlasPos.y + scaledHeight / 2, 0.1f);
        borderGeometry.setLocalScale(scale, scale, 1f);

        // 更新大小标签位置（显示在边框上方）
        sizeLabel.setLocalTranslation(atlasPos.x + scaledWidth / 2, atlasPos.y + scaledHeight + 10, 1);
        sizeLabel.setText(atlasWidth + " x " + atlasHeight);
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

            // 更新边框和大小标签
            updateBorderAndLabel();
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
            // 放大，以鼠标为中心
            zoomAtMouse(1.1f);
        } else if ("MouseWheelDown".equals(name)) {
            // 缩小，以鼠标为中心
            zoomAtMouse(0.9f);
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

        // 重新居中纹理图集和更新边框标签
        updateAtlasTransform();
    }
}
