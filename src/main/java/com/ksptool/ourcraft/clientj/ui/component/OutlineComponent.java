package com.ksptool.ourcraft.clientj.ui.component;

import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.component.AbstractGuiComponent;
import com.simsilica.lemur.core.GuiControl;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

public class OutlineComponent extends AbstractGuiComponent {

    private Node borderNode;
    // 四个独立几何体，初始为 null，按需 attach
    private Geometry top, bottom, left, right;
    private ColorRGBA color;
    private float thickness = 1f;
    private Material mat;
    // 配置项
    private BorderMode mode = BorderMode.INNER;
    private Set<BorderSide> visibleSides = EnumSet.allOf(BorderSide.class); // 默认全开

    public OutlineComponent(ColorRGBA color, float thickness) {
        this(color, thickness, BorderMode.INNER);
    }

    public OutlineComponent(ColorRGBA color, float thickness, BorderMode mode) {
        this.color = color;
        this.thickness = thickness;
        this.mode = mode;
        createNode();
    }

    private void createNode() {
        borderNode = new Node("OutlineBorder");
        // 通用材质
        mat = GuiGlobals.getInstance().createMaterial(color, false).getMaterial();
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);

        // 预创建 Geometry，但是先不 attach，reshape 时决定
        Quad q = new Quad(1, 1);
        top = new Geometry("top", q);
        bottom = new Geometry("bottom", q);
        left = new Geometry("left", q);
        right = new Geometry("right", q);

        applyMaterial();
    }

    private void applyMaterial() {
        if (mat == null) return;
        mat.setColor("Color", color);
        top.setMaterial(mat);
        bottom.setMaterial(mat);
        left.setMaterial(mat);
        right.setMaterial(mat);
    }

    public void setSides(BorderSide... sides) {
        this.visibleSides.clear();
        visibleSides.addAll(Arrays.asList(sides));
        invalidate(); // 触发重新布局
    }

    public void setSides(Set<BorderSide> sides) {
        this.visibleSides = sides;
        invalidate();
    }

    public void setMode(BorderMode mode) {
        this.mode = mode;
        invalidate();
    }

    public void setColor(ColorRGBA color) {
        this.color = color;
        applyMaterial();
    }

    public void setThickness(float thickness) {
        this.thickness = thickness;
        invalidate();
    }

    @Override
    public void attach(GuiControl parent) {
        super.attach(parent);
        parent.getNode().attachChild(borderNode);
    }

    @Override
    public void detach(GuiControl parent) {
        borderNode.removeFromParent();
        super.detach(parent);
    }

    @Override
    public void calculatePreferredSize(Vector3f size) {
        // 边框通常不影响布局大小计算
    }

    @Override
    public void reshape(Vector3f pos, Vector3f size) {
        // 1. 先清理所有子节点，根据 visibleSides 重新挂载
        borderNode.detachAllChildren();
        if (visibleSides.contains(BorderSide.TOP)) borderNode.attachChild(top);
        if (visibleSides.contains(BorderSide.BOTTOM)) borderNode.attachChild(bottom);
        if (visibleSides.contains(BorderSide.LEFT)) borderNode.attachChild(left);
        if (visibleSides.contains(BorderSide.RIGHT)) borderNode.attachChild(right);

        float x = pos.x;
        float y = pos.y;
        float z = pos.z + 0.1f; // 防遮挡
        float w = size.x;
        float h = size.y;
        float t = thickness;

        // 2. 根据模式计算基准矩形
        // 策略：上下边框 (Top/Bottom) 总是负责处理角落。
        //       左右边框 (Left/Right) 总是夹在上下边框之间。

        // 判断当前上下边框是否存在，如果不存在，左右边框需要延伸去填补空白
        boolean hasTop = visibleSides.contains(BorderSide.TOP);
        boolean hasBottom = visibleSides.contains(BorderSide.BOTTOM);

        if (mode == BorderMode.INNER) {
            // --- 内边框模式 ---
            // 范围限制在 [x, x+w] 和 [y, y-h] 内部

            // TOP Line: 贴着顶部向下长
            if (hasTop) {
                top.setLocalTranslation(x, y - t, z);
                top.setLocalScale(w, t, 1);
            }

            // BOTTOM Line: 贴着底部向上长
            if (hasBottom) {
                bottom.setLocalTranslation(x, y - h, z);
                bottom.setLocalScale(w, t, 1);
            }

            // LEFT Line
            // 如果有 Top，Y 起点下移 t；如果有 Bottom，高度减少 t
            float ly = y - (hasTop ? t : 0);
            float lh = h - (hasTop ? t : 0) - (hasBottom ? t : 0);
            left.setLocalTranslation(x, ly - lh, z); // Quad原点在左下角，所以Y设为底部
            left.setLocalScale(t, lh, 1);

            // RIGHT Line
            // 同 Left，位置靠右
            right.setLocalTranslation(x + w - t, ly - lh, z);
            right.setLocalScale(t, lh, 1);

        } else {
            // --- 外边框模式 ---
            // 范围向外扩张

            // TOP Line: 贴着顶部向上长
            // 宽度向左右各扩张 t (涵盖角落)
            if (hasTop) {
                top.setLocalTranslation(x - t, y, z);
                top.setLocalScale(w + 2 * t, t, 1);
            }

            // BOTTOM Line: 贴着底部向下长
            if (hasBottom) {
                bottom.setLocalTranslation(x - t, y - h - t, z);
                bottom.setLocalScale(w + 2 * t, t, 1);
            }

            // LEFT & RIGHT Lines
            // 在外模式下，左右线夹在上下线“之间”的垂直区域
            // 如果上下线存在，它们已经在外面把高度占了；如果不存在，左右线需要去填补
            // 但为了简化逻辑且保证美观（标准矩形描边）：
            // 通常外边框也是 Top/Bottom 占满全宽，Left/Right 填中间。

            float ly = y; // 从原始顶部开始
            float lh = h; // 原始高度

            // 如果 Top 没开，为了闭合，Left/Right 应该向上补 t 吗？
            // 答：不需要。因为 "Outer" 定义是围绕内容的。如果 Top 没开，上方就是空的。
            // 但如果 Top 开了，Top 已经在 y+t 的位置了。Left 不需要动。
            // 唯一的问题是：外边框的 Corner 归谁？
            // 我们约定：Corner 永远归 Horizontal (Top/Bottom)。

            // LEFT Line: x 左移 t
            left.setLocalTranslation(x - t, y - h, z);
            left.setLocalScale(t, h, 1);

            // RIGHT Line: x 右移 w
            right.setLocalTranslation(x + w, y - h, z);
            right.setLocalScale(t, h, 1);

            // 修正：如果 Top/Bottom 关闭，Outer 模式下的 Left/Right 要不要把角补上？
            // 这是一个风格选择。通常 CSS 的 border-left 并不包含 corner。
            // 所以上述逻辑是符合 CSS 标准的 (Corner join logic)。
        }
    }

    // 枚举定义
    public enum BorderMode {
        INNER, // 内边框：画在组件尺寸内部
        OUTER  // 外边框：画在组件尺寸外部
    }

    public enum BorderSide {
        TOP, BOTTOM, LEFT, RIGHT
    }
}