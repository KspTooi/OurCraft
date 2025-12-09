package com.ksptool.ourcraft.prototype.components;

import com.atr.jme.font.TrueTypeFont;
import com.atr.jme.font.shape.TrueTypeNode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.simsilica.lemur.component.AbstractGuiComponent;
import com.simsilica.lemur.core.GuiControl;

/**
 * 这是一个适配器，让 Lemur 能够管理 com.atr.jme.font 的 TrueTypeNode
 */
public class TrueTypeComponent extends AbstractGuiComponent implements Cloneable {

    private final TrueTypeFont<?, ?> font;
    private String text;
    private ColorRGBA color;
    private TrueTypeNode<?> textNode;
    private Node parentNode;

    public TrueTypeComponent(TrueTypeFont<?, ?> font, String text, ColorRGBA color) {
        this.font = font;
        this.text = text;
        this.color = color;
        rebuild();
    }

    /**
     * 当这个组件被挂载到 Lemur 的 Panel (GuiControl) 上时调用
     */
    @Override
    public void attach(GuiControl parent) {
        super.attach(parent);
        this.parentNode = parent.getNode();
        if (textNode != null) {
            parentNode.attachChild(textNode);
        }
    }

    /**
     * 当这个组件被移除时调用
     */
    @Override
    public void detach(GuiControl parent) {
        if (textNode != null) {
            textNode.removeFromParent();
        }
        this.parentNode = null;
        super.detach(parent);
    }

    public void setText(String text) {
        if(this.text.equals(text)) return;
        this.text = text;
        rebuild();
    }

    public void setColor(ColorRGBA color) {
        this.color = color;
        // 注意：某些 TTF 实现可能需要重新生成 Node，或者有 setColor 方法
        // 这里简单起见直接 rebuild，性能稍微低一点但最稳妥
        rebuild(); 
    }

    /**
     * 核心逻辑：重新生成 TTF 节点
     */
    private void rebuild() {
        // 1. 如果旧节点存在，先移除
        if (textNode != null) {
            textNode.removeFromParent();
        }

        // 2. 使用你的库生成新的 TrueTypeNode
        // 参数：文本, 间距(kerning), 颜色
        textNode = font.getText(text, 0, color);

        // 3. 修正坐标系统
        // Lemur 的坐标系是 Y 向下为负，但 JME 字体通常基线在 0
        // 我们通常要把文字向下移动一个 ascent 的距离，使其顶部对齐容器顶部
        float ascent = font.getVisualAscent(text);
        textNode.setLocalTranslation(0, -ascent, 0); // 简单的顶部对齐

        // 4. 如果当前已经挂载到 Panel，就把新节点加上去
        if (parentNode != null) {
            parentNode.attachChild(textNode);
        }

        // 5. 通知 Lemur 组件大小变了，需要重新布局 (Re-layout)
        invalidate();
    }

    /**
     * 核心逻辑：告诉 Lemur 这个控件有多大
     * 这样 Container 里的网格布局才能正确计算位置
     */
    @Override
    public void calculatePreferredSize(Vector3f size) {
        if (font == null || text == null) {
            size.set(0, 0, 0);
            return;
        }
        // 使用 TrueTypeFont 提供的方法计算宽高
        float width = font.getLineWidth(text, 0);
        float height = font.getVisualLineHeight(text);
        
        // 设置 Lemur 需要的尺寸
        size.set(width, height, 0);
    }

    @Override
    public void reshape(Vector3f pos, Vector3f size) {
        // 如果需要处理居中或右对齐，在这里调整 textNode 的 localTranslation
        // 目前默认为左上角对齐
        //reshape(pos, size);
    }

    @Override
    public TrueTypeComponent clone() {
        TrueTypeComponent clone = (TrueTypeComponent) super.clone();
        // TODO: copy mutable state here, so the clone can't change the internals of the original
        return clone;
    }
}