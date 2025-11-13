package com.ksptool.mycraft.core;

import lombok.Getter;
import org.joml.Vector2d;
import lombok.extern.slf4j.Slf4j;

import static org.lwjgl.glfw.GLFW.*;

/**
 * 输入处理类，负责处理键盘、鼠标输入和鼠标锁定
 */
@Getter
@Slf4j
public class Input {
    private final long windowHandle;
    private final boolean[] keys;
    private final boolean[] mouseButtons;
    private final Vector2d mousePosition;
    private final Vector2d mouseDelta;
    private boolean mouseLocked;
    private double scrollX;
    private double scrollY;

    public Input(long windowHandle) {
        this.windowHandle = windowHandle;
        this.keys = new boolean[GLFW_KEY_LAST];
        this.mouseButtons = new boolean[GLFW_MOUSE_BUTTON_LAST];
        this.mousePosition = new Vector2d();
        this.mouseDelta = new Vector2d();
        this.mouseLocked = false;

        glfwSetKeyCallback(windowHandle, (window, key, scancode, action, mods) -> {
            if (key >= 0 && key < keys.length) {
                if (action == GLFW_PRESS) {
                    keys[key] = true;
                } else if (action == GLFW_RELEASE) {
                    keys[key] = false;
                }
            }
        });

        glfwSetMouseButtonCallback(windowHandle, (window, button, action, mods) -> {
            if (button >= 0 && button < mouseButtons.length) {
                if (action == GLFW_PRESS) {
                    mouseButtons[button] = true;
                } else if (action == GLFW_RELEASE) {
                    mouseButtons[button] = false;
                }
            }
        });

        glfwSetCursorPosCallback(windowHandle, (window, xpos, ypos) -> {
            mousePosition.set(xpos, ypos);
        });

        glfwSetScrollCallback(windowHandle, (window, xoffset, yoffset) -> {
            scrollX = xoffset;
            scrollY = yoffset;
        });
    }

    public void update() {
        mouseDelta.set(0, 0);

        if (mouseLocked) {
            double[] x = new double[1];
            double[] y = new double[1];
            glfwGetCursorPos(windowHandle, x, y);
            
            int[] width = new int[1];
            int[] height = new int[1];
            glfwGetWindowSize(windowHandle, width, height);
            double centerX = width[0] / 2.0;
            double centerY = height[0] / 2.0;
            
            mouseDelta.set(x[0] - centerX, y[0] - centerY);
            
            if (Math.abs(mouseDelta.x) > 0.1 || Math.abs(mouseDelta.y) > 0.1) {
                glfwSetCursorPos(windowHandle, centerX, centerY);
                mousePosition.set(centerX, centerY);
            }
        }

        scrollX = 0;
        scrollY = 0;
    }

    public boolean isKeyPressed(int key) {
        if (key >= 0 && key < keys.length) {
            return keys[key];
        }
        return false;
    }

    public boolean isMouseButtonPressed(int button) {
        if (button >= 0 && button < mouseButtons.length) {
            return mouseButtons[button];
        }
        return false;
    }

    public void setMouseLocked(boolean locked) {
        this.mouseLocked = locked;
        if (locked) {
            glfwSetInputMode(windowHandle, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
            int[] width = new int[1];
            int[] height = new int[1];
            glfwGetWindowSize(windowHandle, width, height);
            glfwSetCursorPos(windowHandle, width[0] / 2.0, height[0] / 2.0);
            mousePosition.set(width[0] / 2.0, height[0] / 2.0);
        } else {
            glfwSetInputMode(windowHandle, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
        }
    }

    public Vector2d getMousePosition() {
        return new Vector2d(mousePosition);
    }
}

