package com.ksptool.ourcraft.client.gui;

import com.ksptool.ourcraft.client.Input;
import com.ksptool.ourcraft.client.rendering.GuiRenderer;
import com.ksptool.ourcraft.client.rendering.TextRenderer;
import org.apache.commons.lang3.StringUtils;
import org.joml.Vector2d;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * 创建世界菜单界面类，处理存档选择、世界名称输入和创建操作
 */
public class CreateWorldMenu {
    
    private static final float BUTTON_WIDTH = 200.0f;
    private static final float BUTTON_HEIGHT = 40.0f;
    private static final float INPUT_WIDTH = 400.0f;
    private static final float INPUT_HEIGHT = 40.0f;
    private static final float SAVE_ITEM_HEIGHT = 30.0f;
    private static final float SAVE_LIST_Y = 200.0f;
    private static final float SAVE_LIST_HEIGHT = 200.0f;

    //世界名称输入
    private StringBuilder worldNameInput = new StringBuilder();

    //存档名称输入
    private StringBuilder saveNameInput = new StringBuilder();

    //是否正在输入世界名称
    private boolean isTypingWorldName = false;

    //是否正在输入存档名称
    private boolean isTypingSaveName = false;

    //选中的存档
    private String selectedSave = null;

    //存档列表滚动偏移
    private int saveScrollOffset = 0;

    public void render(GuiRenderer guiRenderer, int windowWidth, int windowHeight, Input input) {
        float centerX = windowWidth / 2.0f;
        float centerY = windowHeight / 2.0f;

        float saveInputY = centerY - 150.0f;
        float worldInputY = centerY - 50.0f;
        float createY = centerY + 30.0f;
        float cancelY = centerY + 80.0f;

        float inputX = centerX - INPUT_WIDTH / 2.0f;
        float buttonX = centerX - BUTTON_WIDTH / 2.0f;

        TextRenderer textRenderer = guiRenderer.getTextRenderer();
        if (textRenderer != null) {
            textRenderer.renderText(guiRenderer, inputX, saveInputY - 25.0f, "存档名称:", 1.0f, new Vector4f(1.0f, 1.0f, 1.0f,1F), windowWidth, windowHeight);
        }

        Vector4f saveInputBgColor = new Vector4f(0.3f, 0.3f, 0.3f, 0.9f);

        if (isTypingSaveName) {
            saveInputBgColor = new Vector4f(0.4f, 0.4f, 0.4f, 0.9f);
        }
        guiRenderer.renderQuad(inputX, saveInputY, INPUT_WIDTH, INPUT_HEIGHT, saveInputBgColor, windowWidth, windowHeight);

        if (textRenderer != null) {
            String saveDisplayText = saveNameInput.length() > 0 ? saveNameInput.toString() : "输入存档名称或选择现有存档...";
            if (isTypingSaveName && saveNameInput.length() == 0) {
                saveDisplayText = "输入存档名称或选择现有存档...";
            }
            float textX = inputX + 10.0f;
            float textY = saveInputY + 10.0f;
            Vector4f textColor = saveNameInput.length() > 0 ? new Vector4f(1.0f, 1.0f, 1.0f, 1.0f) : new Vector4f(0.7f, 0.7f, 0.7f, 1.0f);
            textRenderer.renderText(guiRenderer, textX, textY, saveDisplayText, 1.0f, textColor, windowWidth, windowHeight);
        }

        //List<String> saves = SaveManager.getInstance().getSaveList();
        //renderSaveList(guiRenderer, saves, windowWidth, windowHeight);

        if (textRenderer != null) {
            textRenderer.renderText(guiRenderer, inputX, worldInputY - 25.0f, "世界名称:", 1.0f, new Vector4f(1.0f, 1.0f, 1.0f,1F), windowWidth, windowHeight);
        }

        Vector4f worldInputBgColor = new Vector4f(0.3f, 0.3f, 0.3f, 0.9f);
        if (isTypingWorldName) {
            worldInputBgColor = new Vector4f(0.4f, 0.4f, 0.4f, 0.9f);
        }
        guiRenderer.renderQuad(inputX, worldInputY, INPUT_WIDTH, INPUT_HEIGHT, worldInputBgColor, windowWidth, windowHeight);

        if (textRenderer != null) {
            String worldDisplayText = worldNameInput.length() > 0 ? worldNameInput.toString() : "输入世界名称...";
            if (isTypingWorldName && worldNameInput.length() == 0) {
                worldDisplayText = "输入世界名称...";
            }
            float textX = inputX + 10.0f;
            float textY = worldInputY + 10.0f;
            Vector4f textColor = worldNameInput.length() > 0 ? new Vector4f(1.0f, 1.0f, 1.0f, 1.0f) : new Vector4f(0.7f, 0.7f, 0.7f, 1.0f);
            textRenderer.renderText(guiRenderer, textX, textY, worldDisplayText, 1.0f, textColor, windowWidth, windowHeight);
        }

        Vector2d mousePos = input.getMousePosition();
        boolean createHovered = isMouseOverButton(mousePos.x, mousePos.y, buttonX, createY, BUTTON_WIDTH, BUTTON_HEIGHT);
        boolean cancelHovered = isMouseOverButton(mousePos.x, mousePos.y, buttonX, cancelY, BUTTON_WIDTH, BUTTON_HEIGHT);

        guiRenderer.renderButton(buttonX, createY, BUTTON_WIDTH, BUTTON_HEIGHT, "创建", createHovered, windowWidth, windowHeight);
        guiRenderer.renderButton(buttonX, cancelY, BUTTON_WIDTH, BUTTON_HEIGHT, "取消", cancelHovered, windowWidth, windowHeight);
    }

    private void renderSaveList(GuiRenderer guiRenderer, List<String> saves, int windowWidth, int windowHeight) {
        if (saves == null || saves.isEmpty()) {
            return;
        }

        float listX = windowWidth / 2.0f - 300.0f;
        float currentY = SAVE_LIST_Y;

        int visibleCount = (int) (SAVE_LIST_HEIGHT / SAVE_ITEM_HEIGHT);
        int startIndex = Math.max(0, saveScrollOffset);
        int endIndex = Math.min(saves.size(), startIndex + visibleCount);

        for (int i = startIndex; i < endIndex; i++) {
            String saveName = saves.get(i);
            boolean isSelected = saveName.equals(selectedSave);
            
            Vector4f bgColor;
            if (isSelected) {
                bgColor = new Vector4f(0.3f, 0.5f, 0.8f, 0.9f);
            } else {
                bgColor = new Vector4f(0.2f, 0.2f, 0.2f, 0.9f);
            }

            guiRenderer.renderQuad(listX, currentY, 600.0f, SAVE_ITEM_HEIGHT, bgColor, windowWidth, windowHeight);

            TextRenderer textRenderer = guiRenderer.getTextRenderer();
            if (textRenderer != null) {
                textRenderer.renderText(guiRenderer, listX + 10.0f, currentY + 5.0f, saveName, 1.0f, new Vector4f(1.0f, 1.0f, 1.0f, 1.0f), windowWidth, windowHeight);
            }

            currentY += SAVE_ITEM_HEIGHT;
        }
    }

    public int handleInput(Input input, int windowWidth, int windowHeight) {
        Vector2d mousePos = input.getMousePosition();
        float centerX = windowWidth / 2.0f;
        float centerY = windowHeight / 2.0f;

        float saveInputY = centerY - 150.0f;
        float worldInputY = centerY - 50.0f;
        float createY = centerY + 30.0f;
        float cancelY = centerY + 80.0f;

        float inputX = centerX - INPUT_WIDTH / 2.0f;
        float buttonX = centerX - BUTTON_WIDTH / 2.0f;

        if (input.isMouseButtonPressed(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
            if (isMouseOverButton(mousePos.x, mousePos.y, inputX, saveInputY, INPUT_WIDTH, INPUT_HEIGHT)) {
                isTypingSaveName = true;
                isTypingWorldName = false;
            } else if (isMouseOverButton(mousePos.x, mousePos.y, inputX, worldInputY, INPUT_WIDTH, INPUT_HEIGHT)) {
                isTypingWorldName = true;
                isTypingSaveName = false;
            } else {
                isTypingSaveName = false;
                isTypingWorldName = false;
            }

            //List<String> saves = SaveManager.getInstance().getSaveList();
            float listX = windowWidth / 2.0f - 300.0f;
            float currentY = SAVE_LIST_Y;

            int startIndex = Math.max(0, saveScrollOffset);
            //int endIndex = Math.min(saves.size(), startIndex + (int)(SAVE_LIST_HEIGHT / SAVE_ITEM_HEIGHT));

            /*for (int i = startIndex; i < endIndex; i++) {
                if (isMouseOverButton(mousePos.x, mousePos.y, listX, currentY, 600.0f, SAVE_ITEM_HEIGHT)) {
                    //selectedSave = saves.get(i);
                    saveNameInput.setLength(0);
                    saveNameInput.append(selectedSave);
                    return 0;
                }
                currentY += SAVE_ITEM_HEIGHT;
            }*/

            if (isMouseOverButton(mousePos.x, mousePos.y, buttonX, createY, BUTTON_WIDTH, BUTTON_HEIGHT)) {
                String worldName = worldNameInput.toString().trim();
                String saveName = saveNameInput.toString().trim();
                if (!StringUtils.isBlank(worldName) && !StringUtils.isBlank(saveName)) {
                    return 1;
                }
            }

            if (isMouseOverButton(mousePos.x, mousePos.y, buttonX, cancelY, BUTTON_WIDTH, BUTTON_HEIGHT)) {
                worldNameInput.setLength(0);
                saveNameInput.setLength(0);
                selectedSave = null;
                isTypingWorldName = false;
                isTypingSaveName = false;
                return 2;
            }
        }

        if (isTypingSaveName) {
            handleTextInput(input, saveNameInput);
        }
        if (isTypingWorldName) {
            handleTextInput(input, worldNameInput);
        }

        double scrollY = input.getScrollY();
        if (scrollY != 0) {
            Vector2d mousePosition = input.getMousePosition();
            if (mousePosition.y >= SAVE_LIST_Y && mousePosition.y <= SAVE_LIST_Y + SAVE_LIST_HEIGHT) {
                //List<String> saves = SaveManager.getInstance().getSaveList();
                //int maxScroll = Math.max(0, saves.size() - (int)(SAVE_LIST_HEIGHT / SAVE_ITEM_HEIGHT));
                int maxScroll = 0;
                saveScrollOffset += (int)scrollY;
                if (saveScrollOffset < 0) {
                    saveScrollOffset = 0;
                }
                if (saveScrollOffset > maxScroll) {
                    saveScrollOffset = maxScroll;
                }
            }
        }

        return 0;
    }

    private void handleTextInput(Input input, StringBuilder target) {
        for (int key = GLFW.GLFW_KEY_A; key <= GLFW.GLFW_KEY_Z; key++) {
            if (input.isKeyPressed(key)) {
                char c = (char) ('a' + (key - GLFW.GLFW_KEY_A));
                if (input.isKeyPressed(GLFW.GLFW_KEY_LEFT_SHIFT) ||
                    input.isKeyPressed(GLFW.GLFW_KEY_RIGHT_SHIFT)) {
                    c = Character.toUpperCase(c);
                }
                target.append(c);
                return;
            }
        }

        for (int key = GLFW.GLFW_KEY_0; key <= GLFW.GLFW_KEY_9; key++) {
            if (input.isKeyPressed(key)) {
                char c = (char) ('0' + (key - GLFW.GLFW_KEY_0));
                target.append(c);
                return;
            }
        }

        if (input.isKeyPressed(GLFW.GLFW_KEY_SPACE)) {
            target.append(' ');
        }

        if (input.isKeyPressed(GLFW.GLFW_KEY_BACKSPACE) && target.length() > 0) {
            target.setLength(target.length() - 1);
        }
    }

    public String getWorldName() {
        return worldNameInput.toString().trim();
    }

    public String getSaveName() {
        return saveNameInput.toString().trim();
    }

    public void reset() {
        worldNameInput.setLength(0);
        saveNameInput.setLength(0);
        selectedSave = null;
        isTypingWorldName = false;
        isTypingSaveName = false;
    }

    private boolean isMouseOverButton(double mouseX, double mouseY, float buttonX, float buttonY, float buttonWidth, float buttonHeight) {
        return mouseX >= buttonX && mouseX <= buttonX + buttonWidth &&
               mouseY >= buttonY && mouseY <= buttonY + buttonHeight;
    }
}

