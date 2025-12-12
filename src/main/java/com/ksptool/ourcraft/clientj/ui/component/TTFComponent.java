package com.ksptool.ourcraft.clientj.ui.component;

import com.atr.jme.font.shape.TrueTypeNode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.ksptool.ourcraft.clientj.commons.FontSize;
import com.ksptool.ourcraft.clientj.service.GlobalFontService;
import com.simsilica.lemur.HAlignment;
import com.simsilica.lemur.VAlignment;
import com.simsilica.lemur.component.AbstractGuiComponent;
import com.simsilica.lemur.core.GuiControl;
import lombok.Getter;

public class TTFComponent extends AbstractGuiComponent {

    @Getter
    private String text;
    @Getter
    private FontSize fontSize;
    @Getter
    private ColorRGBA color;

    // 对齐方式，默认居中，模仿 Label 行为
    private HAlignment hAlign = HAlignment.Center;
    private VAlignment vAlign = VAlignment.Center;

    private TrueTypeNode<?> textNode;
    private Node parentNode;

    public TTFComponent(String text, FontSize fontSize, ColorRGBA color) {
        this.text = text;
        this.fontSize = fontSize;
        this.color = color;
    }

    @Override
    public void attach(GuiControl parent) {
        super.attach(parent);
        this.parentNode = parent.getNode();
        refresh(); // 挂载时生成字体
    }

    @Override
    public void detach(GuiControl parent) {
        if (textNode != null) {
            textNode.removeFromParent();
            textNode = null;
        }
        this.parentNode = null;
        super.detach(parent);
    }

    public void setText(String text) {
        if (this.text.equals(text)) return;
        this.text = text;
        refresh();
    }

    public void setHAlignment(HAlignment align) {
        if (this.hAlign != align) {
            this.hAlign = align;
            invalidate(); // 标记需要重新计算布局
        }
    }

    public void setVAlignment(VAlignment align) {
        if (this.vAlign != align) {
            this.vAlign = align;
            invalidate();
        }
    }

    private void refresh() {
        if (parentNode == null) return;

        // 1. 清理旧的
        if (textNode != null) textNode.removeFromParent();

        // 2. 生成新的 (使用你的 GlobalFontService)
        textNode = GlobalFontService.getText(text, fontSize);

        // 如果库支持setColor则设置，否则忽略或遍历设置材质
        // textNode.setColor(color); 

        // 3. 挂载
        parentNode.attachChild(textNode);

        // 4. 告诉 Lemur 尺寸变了
        invalidate();
    }

    @Override
    public void calculatePreferredSize(Vector3f size) {
        if (textNode != null) {
            // 告诉 Lemur：为了放下这段字，我需要这么大的空间
            size.set(textNode.getWidth(), textNode.getHeight(), 0);
        }
    }

    @Override
    public void reshape(Vector3f pos, Vector3f size) {
        if (textNode == null) return;

        float textW = textNode.getWidth();
        float textH = textNode.getHeight();

        // 计算 X 轴偏移
        float x = pos.x;
        switch (hAlign) {
            case Center:
                x += (size.x - textW) * 0.5f;
                break;
            case Right:
                x += size.x - textW;
                break;
            case Left:
            default:
                break;
        }

        // 计算 Y 轴偏移
        // 注意：Lemur 的布局是从上往下的，pos.y 是顶端。
        // JME 的 Y 轴是向上的。
        // 通常 TrueTypeNode 的原点在左上角或基线。这里假设是左上角。
        // 如果发现文字偏上或偏下，请调整这里的计算。
        float y = pos.y;
        switch (vAlign) {
            case Center:
                y -= (size.y - textH) * 0.5f;
                break;
            case Bottom:
                y -= size.y - textH;
                break;
            case Top:
            default:
                break;
        }

        // 你的 TrueTypeNode 可能是以基线为原点，如果发现偏高，可能需要减去一些 ascent
        // y -= textNode.getAscent(); // 视具体情况微调

        textNode.setLocalTranslation(x, y, pos.z + 0.01f); // z + 0.01 保证在背景前面
    }
}