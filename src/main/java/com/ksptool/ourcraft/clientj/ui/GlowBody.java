package com.ksptool.ourcraft.clientj.ui;

import com.jme3.app.Application;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.simsilica.lemur.component.QuadBackgroundComponent;

/**
 * 类似于 HTML 的 <body>
 * 作用：统一坐标系，将原点 (0,0) 强制设定为屏幕左上角。
 * 所有 UI 内容都应该 add 到这里面。
 */
public class GlowBody extends LayoutView {

    private final Application app;

    public GlowBody(Application app) {
        this.app = app;
        this.setName("GlowBody");
        this.setBackground(null);

        // 初始化时调整位置
        resizeAndReposition();
    }

    /**
     * 核心魔法：将此容器移动到屏幕左上角
     * 并设置尺寸为全屏
     */
    public void resizeAndReposition() {
        Camera cam = app.getCamera();
        float screenW = cam.getWidth();
        float screenH = cam.getHeight();

        if (screenW < 10 || screenH < 10) {
            return; // 窗口太小或最小化时，不更新 UI 尺寸，保持原状
        }

        // 设置尺寸为全屏
        this.setPreferredSize(new Vector3f(screenW, screenH, 0));

        // 将此节点的 (0,0) 移动到屏幕左上角
        // JME 的 (0,0) 是左下角。所以我们要向上移动 screenH 的距离
        this.setLocalTranslation(0, screenH, 0);
    }

    /**
     * 在 update 中调用，或者由 ResizeListener 调用
     * 确保窗口大小改变时，UI 依然对其
     */
    public void update() {
        Camera cam = app.getCamera();
        // 简单检测：如果屏幕变了，就重置
        if (cam.getWidth() != this.getPreferredSize().x ||
                cam.getHeight() != this.getPreferredSize().y) {
            resizeAndReposition();
        }
    }

    // 销毁时把自己移除
    public void cleanup() {
        this.removeFromParent();
    }

    public GlowBody bg(ColorRGBA color) {
        this.setBackground(new QuadBackgroundComponent(color));
        return this;
    }


}