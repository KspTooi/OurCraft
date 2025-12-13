package com.ksptool.ourcraft.clientj.ui;

import com.jme3.scene.Node;
import com.simsilica.lemur.Axis;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.FillMode;
import com.simsilica.lemur.component.BorderLayout;
import com.simsilica.lemur.component.BorderLayout.Position;
import com.simsilica.lemur.component.SpringGridLayout;

public class LayoutView extends Container {

    private Layout layout;

    private Axis mainAxis;
    private Axis crossAxis;
    private FillMode mainFillMode;
    private FillMode crossFillMode;

    public LayoutView() {
        super(new SpringGridLayout(Axis.Y, Axis.X, FillMode.None, FillMode.None));
        mainAxis = Axis.Y;
        crossAxis = Axis.X;
        mainFillMode = FillMode.None;
        crossFillMode = FillMode.None;
        layout = Layout.SPRING_GRID;
    }

    /**
     * BorderLayout 是 Lemur 的布局，用于将子元素排列在边框上。
     * BorderLayout.NORTH 表示将子元素排列在顶部。
     * BorderLayout.SOUTH 表示将子元素排列在底部。
     * BorderLayout.WEST 表示将子元素排列在左侧。
     * BorderLayout.EAST 表示将子元素排列在右侧。
     * BorderLayout.CENTER 表示将子元素排列在中心。
     * 北、南、东、西区域的组件通常保持其首选尺寸，并被推向其指定的边缘。
     * 中心 (Center) 区域的组件会占据所有剩余的空间。这通常是放置主要内容或需要伸缩的组件的地方。
     *
     * @return BodyLayout
     */
    public LayoutView layoutBorder() {
        layout = Layout.BORDER;
        this.setLayout(new BorderLayout());
        return this;
    }

    public LayoutView layoutNull() {
        layout = Layout.NULL;
        this.setLayout(null); // 这只改了枚举，没改实际布局
        return this;
    }

    /**
     * 添加子元素到顶部区域(只适用于 BorderLayout)
     *
     * @param child 子元素
     * @return BodyLayout
     */
    public LayoutView addChildToTop(Node child) {
        ensureLayoutMode(Layout.BORDER);
        ((BorderLayout) this.getLayout()).addChild(Position.North, child);
        return this;
    }

    /**
     * 添加子元素到底部区域(只适用于 BorderLayout)
     *
     * @param child 子元素
     * @return BodyLayout
     */
    public LayoutView addChildToBottom(Node child) {
        ensureLayoutMode(Layout.BORDER);
        ((BorderLayout) this.getLayout()).addChild(Position.South, child);
        return this;
    }

    /**
     * 添加子元素到左侧区域(只适用于 BorderLayout)
     *
     * @param child 子元素
     * @return BodyLayout
     */
    public LayoutView addChildToLeft(Node child) {
        ensureLayoutMode(Layout.BORDER);
        ((BorderLayout) this.getLayout()).addChild(Position.West, child);
        return this;
    }

    /**
     * 添加子元素到右侧区域(只适用于 BorderLayout)
     *
     * @param child 子元素
     * @return BodyLayout
     */
    public LayoutView addChildToRight(Node child) {
        ensureLayoutMode(Layout.BORDER);
        ((BorderLayout) this.getLayout()).addChild(Position.East, child);
        return this;
    }

    /**
     * 添加子元素到中心区域(只适用于 BorderLayout)
     *
     * @param child 子元素
     * @return BodyLayout
     */
    public LayoutView addChildToCenter(Node child) {
        ensureLayoutMode(Layout.BORDER);
        ((BorderLayout) this.getLayout()).addChild(Position.Center, child);
        return this;
    }

    /**
     * 设置为横向排列
     *
     * @return BodyLayout
     */
    public LayoutView flexRow() {
        ensureLayoutMode(Layout.SPRING_GRID);
        mainAxis = Axis.X;
        crossAxis = Axis.Y;
        this.setLayout(new SpringGridLayout(mainAxis, crossAxis, mainFillMode, crossFillMode));
        return this;
    }

    /**
     * 设置为纵向排列
     *
     * @return BodyLayout
     */
    public LayoutView flexCol() {
        ensureLayoutMode(Layout.SPRING_GRID);
        mainAxis = Axis.Y;
        crossAxis = Axis.X;
        this.setLayout(new SpringGridLayout(mainAxis, crossAxis, mainFillMode, crossFillMode));
        return this;
    }

    /**
     * 设置为垂直布局（纵向排列）- flexCol 的别名
     *
     * @return LayoutView
     */
    public LayoutView layoutFlexVer() {
        return flexCol();
    }

    /**
     * 设置为水平布局（横向排列）- flexRow 的别名
     *
     * @return LayoutView
     */
    public LayoutView layoutFlexHor() {
        return flexRow();
    }

    /**
     * 设置为高度填充
     *
     * @return BodyLayout
     */
    public LayoutView flexFillHeight() {
        ensureLayoutMode(Layout.SPRING_GRID);

        // 确保只填充垂直轴
        if (mainAxis == Axis.Y) {
            mainFillMode = FillMode.Even;
            crossFillMode = FillMode.None;
        }

        // 确保只填充垂直轴
        if (mainAxis == Axis.X) {
            mainFillMode = FillMode.None;
            crossFillMode = FillMode.Even;
        }

        this.setLayout(new SpringGridLayout(mainAxis, crossAxis, mainFillMode, crossFillMode));
        return this;
    }

    public LayoutView flexFillAll() {
        ensureLayoutMode(Layout.SPRING_GRID);
        mainFillMode = FillMode.Even;
        crossFillMode = FillMode.Even;
        this.setLayout(new SpringGridLayout(mainAxis, crossAxis, mainFillMode, crossFillMode));
        return this;
    }

    /**
     * 设置为宽度填充
     *
     * @return BodyLayout
     */
    public LayoutView flexFillWidth() {
        ensureLayoutMode(Layout.SPRING_GRID);

        // 确保只填充水平轴
        if (mainAxis == Axis.X) {
            crossFillMode = FillMode.Even;
            mainFillMode = FillMode.None;
        }

        // 确保只填充水平轴
        if (mainAxis == Axis.Y) {
            crossFillMode = FillMode.None;
            mainFillMode = FillMode.Even;
        }

        this.setLayout(new SpringGridLayout(mainAxis, crossAxis, mainFillMode, crossFillMode));
        return this;
    }

    /**
     * SpringGridLayout 是 Lemur 的默认布局，用于自动排列子元素。
     * FillMode.None 表示子元素不会自动拉伸，而是根据内容大小自动排列。
     * FillMode.Even 表示子元素会自动拉伸，填充剩余空间。
     * FillMode.Absolute 表示子元素会根据绝对大小排列。
     * FillMode.Parent 表示子元素会根据父元素大小排列。
     *
     * @return BodyLayout
     */
    public LayoutView layoutSpringGrid() {
        layout = Layout.SPRING_GRID;
        this.setLayout(new SpringGridLayout(Axis.Y, Axis.X, FillMode.None, FillMode.None));
        return this;
    }

    /**
     * 确保布局模式为指定模式
     *
     * @param layout 布局模式
     */
    private void ensureLayoutMode(Layout layout) {
        if (this.layout != layout) {
            throw new IllegalStateException("Layout mode is not " + layout);
        }
    }

    private enum Layout {
        BORDER,
        SPRING_GRID,
        NULL,
    }

}
