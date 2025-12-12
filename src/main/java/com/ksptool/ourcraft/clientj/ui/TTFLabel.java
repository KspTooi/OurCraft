package com.ksptool.ourcraft.clientj.ui;

import com.jme3.math.ColorRGBA;
import com.ksptool.ourcraft.clientj.commons.FontSize;
import com.ksptool.ourcraft.clientj.ui.component.TTFComponent;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.HAlignment;
import com.simsilica.lemur.Panel;
import com.simsilica.lemur.VAlignment;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.core.GuiControl;
import com.simsilica.lemur.style.ElementId;

/**
 * 一个行为类似于 Lemur Label 的控件，但使用 TTF 渲染文字。
 */
public class TTFLabel extends Panel {

    private TTFComponent ttfLayer;

    public TTFLabel(String text, FontSize fontSize) {
        this(text, fontSize, ColorRGBA.White);
    }

    public TTFLabel(String text, FontSize fontSize, ColorRGBA color) {
        this(text, fontSize, color, new ElementId("label"), null);
    }

    protected TTFLabel(String text, FontSize fontSize, ColorRGBA color,
                       ElementId elementId, String style) {
        super(false, elementId, style); // false = 先不应用样式，等组件加好了再应用

        // 创建核心渲染组件
        this.ttfLayer = new TTFComponent(text, fontSize, color);

        // 将其添加到 Panel 的控件栈中
        // 注意：这里用 addComponent 而不是 setBackground
        getControl(GuiControl.class).addComponent(ttfLayer);

        // 应用样式 (这会加载背景等)
        GuiGlobals.getInstance().getStyles().applyStyles(this, elementId, style);
    }

    public String getText() {
        return ttfLayer.getText();
    }

    public void setText(String text) {
        ttfLayer.setText(text);
    }

    /**
     * 设置文字水平对齐方式 (Left, Center, Right)
     */
    public void setTextHAlignment(HAlignment align) {
        ttfLayer.setHAlignment(align);
    }


    /**
     * 设置文字垂直对齐方式 (Top, Center, Bottom)
     */
    public void setTextVAlignment(VAlignment align) {
        ttfLayer.setVAlignment(align);
    }

    // 如果需要设置背景颜色
    public void setBackgroundColor(ColorRGBA color) {
        if (getBackground() instanceof QuadBackgroundComponent) {
            ((QuadBackgroundComponent) getBackground()).setColor(color);
        } else {
            setBorder(new QuadBackgroundComponent(color));
        }
    }

    public TTFLabel textAlignCenter() {
        ttfLayer.setHAlignment(HAlignment.Center);
        ttfLayer.setVAlignment(VAlignment.Center);
        return this;
    }

    public TTFLabel textHAlignCenter() {
        ttfLayer.setHAlignment(HAlignment.Center);
        return this;
    }
    public TTFLabel textVAlignCenter() {
        ttfLayer.setVAlignment(VAlignment.Center);
        return this;
    }

    public TTFLabel textAlignLeft() {
        ttfLayer.setHAlignment(HAlignment.Left);
        return this;
    }

    public TTFLabel textAlignRight() {
        ttfLayer.setHAlignment(HAlignment.Right);
        return this;
    }

    public TTFLabel textAlignTop() {
        ttfLayer.setVAlignment(VAlignment.Top);
        return this;
    }

    public TTFLabel textAlignBottom() {
        ttfLayer.setVAlignment(VAlignment.Bottom);
        return this;
    }

}