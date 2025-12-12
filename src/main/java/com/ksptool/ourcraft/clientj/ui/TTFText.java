package com.ksptool.ourcraft.clientj.ui;

import com.atr.jme.font.shape.TrueTypeNode;
import com.atr.jme.font.util.StringContainer;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.ksptool.ourcraft.clientj.commons.FontSize;
import com.ksptool.ourcraft.clientj.service.GlobalFontService;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.component.SpringGridLayout;
import lombok.Getter;

/**
 * TtfText 是一个 Lemur 组件包装器。
 * 它将原生的 TrueTypeNode 包装在 Lemur Container 中，
 * 并自动向 Lemur 布局系统报告正确的尺寸 (PreferredSize)。
 */
public class TTFText extends Container {

    @Getter
    private String text;

    @Getter
    private FontSize fontSize;

    @Getter
    private ColorRGBA color;

    // 内部持有的真实字体节点
    private TrueTypeNode<?> textNode;

    /**
     * 快速构建一个文本组件
     *
     * @param text     初始文本
     * @param fontSize 字体大小枚举
     */
    public TTFText(String text, FontSize fontSize) {
        this(text, fontSize, ColorRGBA.White);
        this.setBackground(null);
    }

    /**
     * 快速构建一个文本组件 (带颜色)
     */
    public TTFText(String text, FontSize fontSize, ColorRGBA color) {
        // 使用 SpringGridLayout 但不重要，因为我们手动管理子节点
        super(new SpringGridLayout());
        this.text = text;
        this.fontSize = fontSize;
        this.color = color;
        refresh();
    }

    /**
     * 设置文本内容并自动重新计算布局尺寸
     */
    public TTFText setText(String text) {
        if (!this.text.equals(text)) {
            this.text = text;
            refresh();
        }
        return this;
    }

    /**
     * 设置颜色并刷新
     */
    public TTFText setColor(ColorRGBA color) {
        this.color = color;
        // 注意：有些 TTF 实现支持直接 setColor，如果不支持则需要重新生成
        // 这里假设我们需要重新生成或更新 Geometry 颜色
        refresh();
        return this;
    }

    /**
     * 核心逻辑：重新生成字体节点并告诉 Lemur 尺寸
     */
    private void refresh() {
        // 1. 清理旧节点
        if (textNode != null) {
            textNode.removeFromParent();
        }

        // 2. 从服务获取新的原生节点 (假设 FontService 支持带颜色的获取，如果不支持请手动设置材质颜色)
        // 这里根据你的 FontService 逻辑，可能需要调整调用方式
        // 假设接口是 fontService.getText(text, size)
        textNode = GlobalFontService.getText(text, fontSize);

        // 如果 getText 不支持颜色，需要手动遍历子 Geometry 设置颜色
        if (color != null) {
            // 简单的颜色设置逻辑，具体取决于 TrueTypeNode 的实现结构
            // textNode.setColor(color) // 如果库支持
        }

        // 3. 修正坐标系统
        // TrueTypeNode 通常原点在基线或左上角。Lemur 组件原点在左上角。
        // 为了让文本完全显示在容器内，通常需要向下移动一个高度（因为JME GUI坐标系Y向上）
        // 下面这个偏移量可能需要根据实际显示效果微调：
        float width = textNode.getWidth();
        float height = textNode.getHeight();

        // 居中/对齐修正：将文本节点挂载到 Container 上
        // 这里我们将文本左下角对齐到容器内部合适位置

        textNode.setHorizontalAlignment(StringContainer.Align.Center);
        textNode.setVerticalAlignment(StringContainer.VAlign.Center);

        this.attachChild(textNode);

        textNode.updateLogicalState(0);
        textNode.updateGeometricState();


        // 4. 关键：告诉 Lemur 这个容器有多大
        this.setPreferredSize(new Vector3f(textNode.getWidth(), textNode.getHeight(), 0));
    }


}