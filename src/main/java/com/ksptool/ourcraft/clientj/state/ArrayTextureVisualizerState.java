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
import com.ksptool.ourcraft.clientj.OurCraftClientJ;
import com.ksptool.ourcraft.clientj.commons.FontSize;
import com.ksptool.ourcraft.clientj.commons.RGBA;
import com.ksptool.ourcraft.clientj.service.ArrayTextureService;
import com.ksptool.ourcraft.clientj.service.ClientStateService;
import com.ksptool.ourcraft.clientj.ui.GlowButton;
import com.ksptool.ourcraft.clientj.ui.TTFLabel;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

/**
 * 数组纹理可视化器，用于显示ArrayTextureService管理的独立纹理
 *
 * 功能特性：
 * - 以网格形式显示所有已加载的纹理
 * - 支持动画纹理的实时预览
 * - 鼠标滚轮缩放（0.1x - 3.0x）
 * - 鼠标左键拖拽移动视图
 * - 显示纹理名称和动画信息
 *
 * 使用方法：
 * ArrayTextureVisualizerState visualizer = new ArrayTextureVisualizerState(client);
 * stateManager.attach(visualizer);
 */
@Slf4j
public class ArrayTextureVisualizerState extends BaseAppState implements ActionListener, AnalogListener {

    private final OurCraftClientJ client;
    private final ClientStateService clientStateService;

    private Node guiNode;
    private Node textureGridNode;
    private GlowButton backButton;
    private TTFLabel infoLabel;

    // 纹理网格相关
    private final List<TextureGeometry> textureGeometries = new ArrayList<>();
    private final int TEXTURE_SIZE = 64; // 显示时每个纹理的统一尺寸
    private final int GRID_SPACING = 80; // 网格间距
    private final int GRID_COLS = 8; // 每行显示的纹理数量

    // 缩放和拖拽相关
    private float scale = 1.0f;
    private float minScale = 0.1f;
    private float maxScale = 3.0f;
    private Vector3f dragOffset = new Vector3f();
    private boolean isDragging = false;
    private Vector2f lastMousePos = new Vector2f();

    // 动画相关
    private float animationTime = 0.0f;

    public ArrayTextureVisualizerState(OurCraftClientJ client) {
        this.client = client;
        this.clientStateService = client.getClientStateService();
    }

    @Override
    protected void initialize(Application app) {
        guiNode = new Node("ArrayTextureVisualizer");
        textureGridNode = new Node("TextureGrid");
        guiNode.attachChild(textureGridNode);

        try {
            // 获取ArrayTextureService实例
            ArrayTextureService arrayTextureService = ArrayTextureService.getInstance();

            // 加载纹理
            arrayTextureService.loadTextures();

            // 创建纹理网格显示
            createTextureGrid(arrayTextureService);

            // 创建信息标签
            createInfoLabel();

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

            log.info("数组纹理可视化器初始化完成，纹理数量: {}", textureGeometries.size());

        } catch (Exception e) {
            log.error("初始化数组纹理可视化器失败", e);
        }
    }

    @Override
    protected void onEnable() {
        client.getGuiNode().attachChild(guiNode);
        log.info("数组纹理可视化器已启用");
    }

    @Override
    protected void onDisable() {
        guiNode.removeFromParent();
        removeInput();
        log.info("数组纹理可视化器已禁用");
        // 重置缩放和拖拽状态
        scale = 1.0f;
        dragOffset.set(0, 0, 0);
        isDragging = false;
        lastMousePos.set(0, 0);
        animationTime = 0.0f;
    }

    @Override
    protected void cleanup(Application app) {
        textureGridNode.detachAllChildren();
        guiNode.detachAllChildren();
        textureGeometries.clear();
    }

    @Override
    public void update(float tpf) {
        // 更新动画纹理
        animationTime += tpf;
        updateAnimatedTextures();

        // 更新鼠标悬浮显示
        updateHoverDisplay();
    }

    /**
     * 创建纹理网格显示
     */
    private void createTextureGrid(ArrayTextureService arrayTextureService) {
        // 获取所有纹理名称
        Set<String> textureNames = arrayTextureService.getTextureNames();

        if (textureNames.isEmpty()) {
            log.warn("没有找到任何纹理");
            return;
        }

        // 按字母顺序排序
        List<String> sortedNames = new ArrayList<>(textureNames);
        sortedNames.sort(String::compareToIgnoreCase);

        int index = 0;
        for (String textureName : sortedNames) {
            // 检查纹理是否存在于数组纹理服务中
            if (arrayTextureService.getTextureInfo(textureName) != null) {
                createTextureGeometry(textureName, arrayTextureService, index);
                index++;
            }
        }

        // 居中整个网格
        centerGrid();
    }

    /**
     * 创建单个纹理几何体
     */
    private void createTextureGeometry(String textureName, ArrayTextureService arrayTextureService, int index) {
        // 计算网格位置
        int row = index / GRID_COLS;
        int col = index % GRID_COLS;

        float x = col * GRID_SPACING;
        float y = -row * GRID_SPACING; // Y轴向下为正

        // 创建四边形
        Mesh quad = new Quad(TEXTURE_SIZE, TEXTURE_SIZE);
        Geometry geometry = new Geometry("Texture_" + textureName, quad);

        // 创建材质 - 使用自定义数组纹理材质
        Material material = createArrayTextureMaterial(arrayTextureService, textureName);
        geometry.setMaterial(material);
        geometry.setLocalTranslation(x, y, 0);

        // 获取纹理信息
        ArrayTextureService.TextureLayerInfo layerInfo = arrayTextureService.getTextureInfo(textureName);

        // 创建标签（默认隐藏）
        String labelText = getDisplayName(textureName);
        if (layerInfo != null && layerInfo.isAnimated) {
            labelText += " (动画)";
        }
        TTFLabel nameLabel = new TTFLabel(labelText, FontSize.SMALL, RGBA.of(200, 200, 200, 255));
        nameLabel.textAlignCenter();
        nameLabel.setPreferredSize(nameLabel.getPreferredSize());
        nameLabel.setLocalTranslation(x, y - TEXTURE_SIZE / 2 - 15, 1);
        nameLabel.setCullHint(com.jme3.scene.Spatial.CullHint.Always); // 默认隐藏

        // 添加到场景
        textureGridNode.attachChild(geometry);
        textureGridNode.attachChild(nameLabel);

        // 记录纹理几何体信息
        TextureGeometry texGeom = new TextureGeometry(geometry, nameLabel,
                textureName, layerInfo, x, y);
        textureGeometries.add(texGeom);
    }

    /**
     * 创建数组纹理材质
     */
    private Material createArrayTextureMaterial(ArrayTextureService arrayTextureService, String textureName) {
        // 使用自定义材质定义来采样数组纹理的特定层
        Material material = new Material(client.getAssetManager(), "MatDefs/ArrayTexture.j3md");

        // 设置数组纹理
        material.setTexture("ArrayTex", arrayTextureService.getTextureArray());

        // 获取层信息
        ArrayTextureService.TextureLayerInfo layerInfo = arrayTextureService.getTextureInfo(textureName);
        if (layerInfo != null) {
            // 设置层索引 (对于静态纹理，使用起始索引；动画纹理会在运行时更新)
            material.setInt("Layer", layerInfo.index);
        }

        material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        return material;
    }


    /**
     * 居中整个网格
     */
    private void centerGrid() {
        if (textureGeometries.isEmpty()) {
            return;
        }

        // 计算网格的总尺寸
        int totalRows = (textureGeometries.size() + GRID_COLS - 1) / GRID_COLS;
        float totalWidth = (GRID_COLS - 1) * GRID_SPACING + TEXTURE_SIZE;
        float totalHeight = (totalRows - 1) * GRID_SPACING + TEXTURE_SIZE;

        // 计算居中偏移
        float screenWidth = client.getCamera().getWidth();
        float screenHeight = client.getCamera().getHeight();
        float offsetX = (screenWidth - totalWidth) / 2;
        float offsetY = (screenHeight + totalHeight) / 2; // Y轴向上为正

        // 应用居中偏移
        textureGridNode.setLocalTranslation(offsetX, offsetY, 0);
    }

    /**
     * 创建信息标签
     */
    private void createInfoLabel() {
        infoLabel = new TTFLabel("数组纹理可视化器 - 缩放: 鼠标滚轮 | 拖拽: 鼠标左键",
                FontSize.SMALL, RGBA.of(150, 150, 150, 255));
        infoLabel.setLocalTranslation(20, 30, 1);
        guiNode.attachChild(infoLabel);
    }

    /**
     * 获取显示名称
     */
    private String getDisplayName(String textureName) {
        if (StringUtils.isBlank(textureName)) {
            return "Unknown";
        }
        return textureName.replace(".png", "");
    }

    /**
     * 更新动画纹理
     */
    private void updateAnimatedTextures() {
        for (TextureGeometry texGeom : textureGeometries) {
            if (texGeom.isAnimated && texGeom.layerInfo != null) {
                // 计算当前动画帧的层索引
                int frameIndex = (int)(animationTime / texGeom.layerInfo.frameTime) % texGeom.layerInfo.frameCount;
                int currentLayer = texGeom.layerInfo.index + frameIndex;

                // 更新材质中的层索引
                texGeom.geometry.getMaterial().setInt("Layer", currentLayer);
            }
        }
    }

    /**
     * 更新鼠标悬浮显示
     */
    private void updateHoverDisplay() {
        if (textureGeometries.isEmpty()) {
            return;
        }

        // 获取鼠标位置
        Vector2f mousePos = client.getInputManager().getCursorPosition();

        // 转换为网格坐标系
        Vector3f gridPos = textureGridNode.getLocalTranslation();
        float scale = this.scale;

        // 计算鼠标在网格中的相对位置
        float relativeX = (mousePos.x - gridPos.x) / scale;
        float relativeY = (mousePos.y - gridPos.y) / scale;

        TextureGeometry hoveredGeometry = null;

        // 检查鼠标是否悬浮在某个纹理上
        for (TextureGeometry texGeom : textureGeometries) {
            float texLeft = texGeom.baseX - TEXTURE_SIZE / 2;
            float texRight = texGeom.baseX + TEXTURE_SIZE / 2;
            float texTop = texGeom.baseY + TEXTURE_SIZE / 2;
            float texBottom = texGeom.baseY - TEXTURE_SIZE / 2;

            if (relativeX >= texLeft && relativeX <= texRight &&
                relativeY >= texBottom && relativeY <= texTop) {
                hoveredGeometry = texGeom;
                break;
            }
        }

        // 隐藏所有标签
        for (TextureGeometry texGeom : textureGeometries) {
            texGeom.nameLabel.setCullHint(com.jme3.scene.Spatial.CullHint.Always);
        }

        // 显示悬浮的纹理标签
        if (hoveredGeometry != null) {
            hoveredGeometry.nameLabel.setCullHint(com.jme3.scene.Spatial.CullHint.Never);

            // 更新标签文本，显示更多信息
            String labelText = getDisplayName(hoveredGeometry.textureName);
            if (hoveredGeometry.isAnimated) {
                labelText += String.format(" (%dx%d)", hoveredGeometry.layerInfo.frameCount,
                                         hoveredGeometry.layerInfo.frameTime > 0 ? (int)(1.0f / hoveredGeometry.layerInfo.frameTime) : 0);
            }
            hoveredGeometry.nameLabel.setText(labelText);
        }
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
     */
    private void zoomAtMouse(float zoomFactor) {
        // 计算新的缩放比例
        float newScale = Math.max(minScale, Math.min(maxScale, scale * zoomFactor));

        if (newScale == scale) {
            return;
        }

        // 获取鼠标位置
        Vector2f mousePos = client.getInputManager().getCursorPosition();

        // 获取网格的当前位置
        Vector3f gridPos = textureGridNode.getLocalTranslation();

        // 计算鼠标相对网格的位置
        float relativeX = (mousePos.x - gridPos.x) / scale;
        float relativeY = (mousePos.y - gridPos.y) / scale;

        // 更新缩放
        scale = newScale;

        // 计算新的位置，保持鼠标指向的相对位置不变
        float newGridX = mousePos.x - relativeX * scale;
        float newGridY = mousePos.y - relativeY * scale;

        // 更新拖拽偏移
        dragOffset.x = newGridX - (client.getCamera().getWidth() - getGridWidth() * scale) / 2;
        dragOffset.y = newGridY - (client.getCamera().getHeight() + getGridHeight() * scale) / 2;

        updateGridTransform();
    }

    /**
     * 获取网格宽度
     */
    private float getGridWidth() {
        if (textureGeometries.isEmpty()) {
            return 0;
        }
        int cols = Math.min(textureGeometries.size(), GRID_COLS);
        return (cols - 1) * GRID_SPACING + TEXTURE_SIZE;
    }

    /**
     * 获取网格高度
     */
    private float getGridHeight() {
        if (textureGeometries.isEmpty()) {
            return 0;
        }
        int rows = (textureGeometries.size() + GRID_COLS - 1) / GRID_COLS;
        return (rows - 1) * GRID_SPACING + TEXTURE_SIZE;
    }

    /**
     * 更新网格变换
     */
    private void updateGridTransform() {
        // 计算居中位置加上拖拽偏移
        float screenWidth = client.getCamera().getWidth();
        float screenHeight = client.getCamera().getHeight();

        float centerX = (screenWidth - getGridWidth() * scale) / 2 + dragOffset.x;
        float centerY = (screenHeight + getGridHeight() * scale) / 2 + dragOffset.y;

        textureGridNode.setLocalTranslation(centerX, centerY, 0);
        textureGridNode.setLocalScale(scale, scale, 1f);

        // 更新信息标签
        if (infoLabel != null) {
            infoLabel.setText(String.format("数组纹理可视化器 - 缩放: %.1fx | 纹理数: %d | 鼠标滚轮缩放 | 鼠标左键拖拽",
                    scale, textureGeometries.size()));
        }
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
            updateGridTransform();
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

        // 更新信息标签位置
        if (infoLabel != null) {
            infoLabel.setLocalTranslation(20, 30, 1);
        }

        // 重新居中网格
        updateGridTransform();
    }

    /**
     * 纹理几何体信息类
     */
    private static class TextureGeometry {
        final Geometry geometry;
        final TTFLabel nameLabel;
        final String textureName;
        final ArrayTextureService.TextureLayerInfo layerInfo;
        final boolean isAnimated;
        final float baseX;
        final float baseY;

        TextureGeometry(Geometry geometry, TTFLabel nameLabel,
                       String textureName, ArrayTextureService.TextureLayerInfo layerInfo,
                       float baseX, float baseY) {
            this.geometry = geometry;
            this.nameLabel = nameLabel;
            this.textureName = textureName;
            this.layerInfo = layerInfo;
            this.isAnimated = layerInfo != null && layerInfo.isAnimated;
            this.baseX = baseX;
            this.baseY = baseY;
        }
    }
}
