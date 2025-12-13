package com.ksptool.ourcraft.clientj.ui;

import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.ksptool.ourcraft.clientj.commons.FontSize;
import com.ksptool.ourcraft.clientj.commons.RGBA;
import com.simsilica.lemur.event.DefaultMouseListener;
import com.simsilica.lemur.event.MouseEventControl;

/**
 * 一个可点击的按钮组件，带有悬停效果
 */
public class GlowButton extends GlowDiv {

    private final TTFLabel label;
    private ColorRGBA normalBgColor;
    private ColorRGBA hoverBgColor;
    private ColorRGBA pressedBgColor;
    private Runnable clickHandler;

    private boolean isHovered = false;
    private boolean isPressed = false;

    public GlowButton(String text) {
        this(text, FontSize.NORMAL);
    }

    public GlowButton(String text, FontSize fontSize) {
        super();

        // 创建标签
        this.label = new TTFLabel(text, fontSize, ColorRGBA.White);
        this.label.textAlignCenter();

        // 设置默认颜色
        this.normalBgColor = RGBA.of(60, 60, 60, 200);
        this.hoverBgColor = RGBA.of(80, 80, 80, 220);
        this.pressedBgColor = RGBA.of(40, 40, 40, 240);

        // 应用默认样式
        this.bg(normalBgColor);
        this.border(RGBA.of(100, 100, 100, 255), 2);

        // 添加标签到按钮
        this.attachChild(label);

        // 设置鼠标事件监听
        setupMouseListener();
    }

    /**
     * 设置按钮文本
     */
    public GlowButton text(String text) {
        this.label.setText(text);
        return this;
    }

    /**
     * 设置点击回调
     */
    public GlowButton onClick(Runnable callback) {
        this.clickHandler = callback;
        return this;
    }

    /**
     * 设置正常状态背景色
     */
    public GlowButton normalColor(ColorRGBA color) {
        this.normalBgColor = color;
        if (!isHovered && !isPressed) {
            this.bg(color);
        }
        return this;
    }

    /**
     * 设置悬停状态背景色
     */
    public GlowButton hoverColor(ColorRGBA color) {
        this.hoverBgColor = color;
        if (isHovered && !isPressed) {
            this.bg(color);
        }
        return this;
    }

    /**
     * 设置按下状态背景色
     */
    public GlowButton pressedColor(ColorRGBA color) {
        this.pressedBgColor = color;
        if (isPressed) {
            this.bg(color);
        }
        return this;
    }

    /**
     * 设置文本颜色
     */
    public GlowButton textColor(ColorRGBA color) {
        this.label.setBackgroundColor(color);
        return this;
    }

    @Override
    public GlowButton size(float x, float y) {
        super.size(x, y);
        // 让标签填满按钮
        label.setPreferredSize(new Vector3f(x, y, 0));
        label.setSize(label.getPreferredSize());
        return this;
    }

    @Override
    public GlowButton border(ColorRGBA color, float width) {
        super.border(color, width);
        return this;
    }

    @Override
    public GlowButton border(ColorRGBA color, float width,
            com.ksptool.ourcraft.clientj.ui.component.OutlineComponent.BorderMode mode) {
        super.border(color, width, mode);
        return this;
    }

    private void setupMouseListener() {
        // 确保有 MouseEventControl
        MouseEventControl.addListenersToSpatial(this, new DefaultMouseListener() {
            @Override
            protected void click(MouseButtonEvent event, Spatial target, Spatial capture) {
                if (event.isPressed()) {
                    isPressed = true;
                    updateBackground();
                } else if (event.isReleased()) {
                    isPressed = false;
                    updateBackground();
                    // 触发点击回调
                    if (clickHandler != null && isHovered) {
                        clickHandler.run();
                    }
                }
            }

            @Override
            public void mouseEntered(MouseMotionEvent event, Spatial target, Spatial capture) {
                isHovered = true;
                updateBackground();
            }

            @Override
            public void mouseExited(MouseMotionEvent event, Spatial target, Spatial capture) {
                isHovered = false;
                isPressed = false;
                updateBackground();
            }
        });
    }

    private void updateBackground() {
        if (isPressed) {
            this.bg(pressedBgColor);
        } else if (isHovered) {
            this.bg(hoverBgColor);
        } else {
            this.bg(normalBgColor);
        }
    }
}
