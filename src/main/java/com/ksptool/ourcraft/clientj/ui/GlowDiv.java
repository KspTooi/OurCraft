package com.ksptool.ourcraft.clientj.ui;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.ksptool.ourcraft.clientj.ui.component.OutlineComponent;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.core.GuiControl;
import lombok.Getter;

public class GlowDiv extends LayoutView {

    private OutlineComponent outlineComp;

    @Getter
    private boolean centerInParent = false;

    // 设置 ID 或 名称
    public GlowDiv id(String name) {
        this.setName(name);
        return this;
    }

    public GlowDiv bg(ColorRGBA color) {
        this.setBackground(new QuadBackgroundComponent(color));
        //更新尺寸
        refreshSize();
        return this;
    }

    public GlowDiv size(float x, float y) {
        this.setPreferredSize(new Vector3f(x, y, 0));
        this.setSize(this.getPreferredSize());
        return this;
    }

    public GlowDiv zIndex(float z) {
        this.setLocalTranslation(getLocalTranslation().x, getLocalTranslation().y, z);
        return this;
    }

    /**
     * 设置全边框 (默认内边框)
     */
    public GlowDiv border(ColorRGBA color, float width) {
        return border(color, width, OutlineComponent.BorderMode.INNER, OutlineComponent.BorderSide.values());
    }

    public GlowDiv borderTop(ColorRGBA color, float width) {
        return border(color, width, OutlineComponent.BorderMode.INNER, OutlineComponent.BorderSide.TOP);
    }

    public GlowDiv borderBottom(ColorRGBA color, float width) {
        return border(color, width, OutlineComponent.BorderMode.INNER, OutlineComponent.BorderSide.BOTTOM);
    }

    public GlowDiv borderLeft(ColorRGBA color, float width) {
        return border(color, width, OutlineComponent.BorderMode.INNER, OutlineComponent.BorderSide.LEFT);
    }

    public GlowDiv borderRight(ColorRGBA color, float width) {
        return border(color, width, OutlineComponent.BorderMode.INNER, OutlineComponent.BorderSide.RIGHT);
    }

    /**
     * 设置边框 (指定模式: 内/外)
     */
    public GlowDiv border(ColorRGBA color, float width, OutlineComponent.BorderMode mode) {
        return border(color, width, mode, OutlineComponent.BorderSide.values());
    }

    /**
     * 设置部分边框 (例如只显示左边)
     */
    public GlowDiv border(ColorRGBA color, float width, OutlineComponent.BorderSide... sides) {
        return border(color, width, OutlineComponent.BorderMode.INNER, sides);
    }

    /**
     * 全能设置方法
     */
    public GlowDiv border(ColorRGBA color, float width, OutlineComponent.BorderMode mode,
                          OutlineComponent.BorderSide... sides) {
        GuiControl control = getControl(GuiControl.class);

        if (outlineComp == null) {
            outlineComp = new OutlineComponent(color, width, mode);
            control.addComponent(outlineComp);
        } else {
            outlineComp.setColor(color);
            outlineComp.setThickness(width);
            outlineComp.setMode(mode);
        }

        // 更新显示的边
        outlineComp.setSides(sides);

        //更新尺寸
        refreshSize();
        return this;
    }

    public void centerInParent(boolean centerInParent) {
        this.centerInParent = centerInParent;
        centerInParentInner();
    }

    private void centerInParentInner() {

        if (!centerInParent) {
            return;
        }

        if (getParent() == null) {
            return;
        }

        // 获取父容器（屏幕）尺寸
        // 建议直接用 body 的尺寸，比调用 cam 更符合 UI 逻辑

        var parent = getParent();

        if (parent instanceof Container c) {
            Vector3f parentSize = c.getPreferredSize();

            float pW = parentSize.x;
            float pH = parentSize.y;

            // 获取子容器尺寸
            Vector3f childSize = this.getPreferredSize();
            float cW = childSize.x;
            float cH = childSize.y;

            // 计算坐标
            // X轴：(父宽 - 子宽) / 2
            float x = (pW - cW) / 2;

            // Y轴：-(父高 - 子高) / 2
            // 注意：因为是从左上角向下排，所以 Y 应该是负值
            float y = -(pH - cH) / 2;

            // 设置坐标 (Z轴设为1或更高，确保在背景之上)
            this.setLocalTranslation(x, y, 1);

        }
    }

    // 辅助方法
    private void refreshSize() {
        if (getPreferredSize() != null) {
            // 再次设置尺寸，通知新加入的组件（背景/边框）更新网格
            setSize(getPreferredSize());
        }
    }


}
