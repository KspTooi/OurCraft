package com.ksptool.ourcraft.client.gui;

import com.ksptool.ourcraft.client.Input;
import com.ksptool.ourcraft.client.rendering.GuiRenderer;
import org.joml.Vector2d;

/**
 * 主菜单界面类，显示游戏主菜单并处理用户选择
 */
public class MainMenu {

    //按钮宽度
    private static final float BUTTON_WIDTH = 200.0f;

    //按钮高度
    private static final float BUTTON_HEIGHT = 40.0f;

    //按钮间距
    private static final float BUTTON_SPACING = 10.0f;

    public void render(GuiRenderer guiRenderer, int windowWidth, int windowHeight, Input input) {
        float centerX = windowWidth / 2.0f;
        float startY = windowHeight / 2.0f - 60.0f;

        float singleplayerY = startY;
        float multiplayerY = startY + BUTTON_HEIGHT + BUTTON_SPACING;
        float optionsY = startY + (BUTTON_HEIGHT + BUTTON_SPACING) * 2;
        float exitY = startY + (BUTTON_HEIGHT + BUTTON_SPACING) * 3;

        float buttonX = centerX - BUTTON_WIDTH / 2.0f;

        Vector2d mousePos = input.getMousePosition();

        boolean singleplayerHovered = isMouseOverButton(mousePos.x, mousePos.y, buttonX, singleplayerY, BUTTON_WIDTH, BUTTON_HEIGHT);
        boolean multiplayerHovered = isMouseOverButton(mousePos.x, mousePos.y, buttonX, multiplayerY, BUTTON_WIDTH, BUTTON_HEIGHT);
        boolean optionsHovered = isMouseOverButton(mousePos.x, mousePos.y, buttonX, optionsY, BUTTON_WIDTH, BUTTON_HEIGHT);
        boolean exitHovered = isMouseOverButton(mousePos.x, mousePos.y, buttonX, exitY, BUTTON_WIDTH, BUTTON_HEIGHT);

        guiRenderer.renderButton(buttonX, singleplayerY, BUTTON_WIDTH, BUTTON_HEIGHT, "单人游戏", singleplayerHovered, windowWidth, windowHeight);
        guiRenderer.renderButton(buttonX, multiplayerY, BUTTON_WIDTH, BUTTON_HEIGHT, "多人游戏", multiplayerHovered, windowWidth, windowHeight);
        guiRenderer.renderButton(buttonX, optionsY, BUTTON_WIDTH, BUTTON_HEIGHT, "选项", optionsHovered, windowWidth, windowHeight);
        guiRenderer.renderButton(buttonX, exitY, BUTTON_WIDTH, BUTTON_HEIGHT, "退出游戏", exitHovered, windowWidth, windowHeight);
    }

    //上一帧鼠标按钮是否按下
    private boolean mouseButtonPressedLastFrame = false;

    public int handleInput(Input input, int windowWidth, int windowHeight) {
        Vector2d mousePos = input.getMousePosition();
        float centerX = windowWidth / 2.0f;
        float startY = windowHeight / 2.0f - 60.0f;

        float singleplayerY = startY;
        float multiplayerY = startY + BUTTON_HEIGHT + BUTTON_SPACING;
        float optionsY = startY + (BUTTON_HEIGHT + BUTTON_SPACING) * 2;
        float exitY = startY + (BUTTON_HEIGHT + BUTTON_SPACING) * 3;

        float buttonX = centerX - BUTTON_WIDTH / 2.0f;

        boolean mousePressed = input.isMouseButtonPressed(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
        boolean mouseJustPressed = mousePressed && !mouseButtonPressedLastFrame;
        mouseButtonPressedLastFrame = mousePressed;

        if (mouseJustPressed) {
            if (isMouseOverButton(mousePos.x, mousePos.y, buttonX, singleplayerY, BUTTON_WIDTH, BUTTON_HEIGHT)) {
                return 1;
            }
            if (isMouseOverButton(mousePos.x, mousePos.y, buttonX, multiplayerY, BUTTON_WIDTH, BUTTON_HEIGHT)) {
                return 2;
            }
            if (isMouseOverButton(mousePos.x, mousePos.y, buttonX, optionsY, BUTTON_WIDTH, BUTTON_HEIGHT)) {
                return 3;
            }
            if (isMouseOverButton(mousePos.x, mousePos.y, buttonX, exitY, BUTTON_WIDTH, BUTTON_HEIGHT)) {
                return 4;
            }
        }

        return 0;
    }

    private boolean isMouseOverButton(double mouseX, double mouseY, float buttonX, float buttonY, float buttonWidth, float buttonHeight) {
        return mouseX >= buttonX && mouseX <= buttonX + buttonWidth &&
               mouseY >= buttonY && mouseY <= buttonY + buttonHeight;
    }
}

