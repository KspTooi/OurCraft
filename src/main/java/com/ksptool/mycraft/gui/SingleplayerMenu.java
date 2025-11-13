package com.ksptool.mycraft.gui;

import com.ksptool.mycraft.core.Input;
import com.ksptool.mycraft.rendering.GuiRenderer;
import com.ksptool.mycraft.rendering.TextRenderer;
import com.ksptool.mycraft.world.WorldManager;
import com.ksptool.mycraft.world.save.SaveManager;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.joml.Vector2d;
import org.joml.Vector4f;

import java.util.List;

/**
 * 单人游戏菜单界面类，显示已保存的存档列表和世界列表并处理选择
 */
public class SingleplayerMenu {
    private static final float BUTTON_WIDTH = 200.0f;
    private static final float BUTTON_HEIGHT = 40.0f;
    private static final float BUTTON_SPACING = 10.0f;
    private static final float WORLD_ITEM_HEIGHT = 30.0f;
    private static final float WORLD_LIST_Y = 150.0f;
    private static final float WORLD_LIST_HEIGHT = 400.0f;
    private static final float SAVE_LIST_Y = 100.0f;
    private static final float SAVE_LIST_HEIGHT = 200.0f;

    //世界列表滚动偏移
    private int scrollOffset = 0;

    //存档列表滚动偏移
    private int saveScrollOffset = 0;

    //选中的存档
    @Getter
    private String selectedSave = null;

    //选中的世界
    @Getter
    private String selectedWorld = null;

    public void render(GuiRenderer guiRenderer, int windowWidth, int windowHeight, Input input) {
        float centerX = windowWidth / 2.0f;

        float createWorldY = 50.0f;
        float backY = windowHeight - 60.0f;

        float buttonX = centerX - BUTTON_WIDTH / 2.0f;

        Vector2d mousePos = input.getMousePosition();

        boolean createWorldHovered = isMouseOverButton(mousePos.x, mousePos.y, buttonX, createWorldY, BUTTON_WIDTH, BUTTON_HEIGHT);
        boolean backHovered = isMouseOverButton(mousePos.x, mousePos.y, buttonX, backY, BUTTON_WIDTH, BUTTON_HEIGHT);

        guiRenderer.renderButton(buttonX, createWorldY, BUTTON_WIDTH, BUTTON_HEIGHT, "创建新世界", createWorldHovered, windowWidth, windowHeight);
        guiRenderer.renderButton(buttonX, backY, BUTTON_WIDTH, BUTTON_HEIGHT, "返回", backHovered, windowWidth, windowHeight);

        List<String> saves = SaveManager.getInstance().getSaveList();
        renderSaveList(guiRenderer, saves, windowWidth, windowHeight);

        if (StringUtils.isNotBlank(selectedSave)) {
            List<String> worlds = WorldManager.getInstance().getWorldList(selectedSave);
        renderWorldList(guiRenderer, worlds, windowWidth, windowHeight);
        }
    }

    private void renderSaveList(GuiRenderer guiRenderer, List<String> saves, int windowWidth, int windowHeight) {
        if (saves == null || saves.isEmpty()) {
            TextRenderer textRenderer = guiRenderer.getTextRenderer();
            if (textRenderer != null) {
                float centerX = windowWidth / 2.0f;
                float textX = centerX - textRenderer.getTextWidth("没有已保存的存档", 1.0f) / 2.0f;
                textRenderer.renderText(guiRenderer, textX, SAVE_LIST_Y + 50.0f, "没有已保存的存档", 1.0f, new Vector4f(1.0f, 1.0f, 1.0f, 1.0f), windowWidth, windowHeight);
            }
            return;
        }

        float listX = windowWidth / 2.0f - 300.0f;
        float currentY = SAVE_LIST_Y;

        int visibleCount = (int) (SAVE_LIST_HEIGHT / WORLD_ITEM_HEIGHT);
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

            guiRenderer.renderQuad(listX, currentY, 600.0f, WORLD_ITEM_HEIGHT, bgColor, windowWidth, windowHeight);

            TextRenderer textRenderer = guiRenderer.getTextRenderer();
            if (textRenderer != null) {
                textRenderer.renderText(guiRenderer, listX + 10.0f, currentY + 5.0f, saveName, 1.0f, new Vector4f(1.0f, 1.0f, 1.0f, 1.0f), windowWidth, windowHeight);
            }

            currentY += WORLD_ITEM_HEIGHT;
        }
    }

    private void renderWorldList(GuiRenderer guiRenderer, List<String> worlds, int windowWidth, int windowHeight) {
        if (worlds == null || worlds.isEmpty()) {
            TextRenderer textRenderer = guiRenderer.getTextRenderer();
            if (textRenderer != null) {
                float centerX = windowWidth / 2.0f;
                float textX = centerX - textRenderer.getTextWidth("没有已保存的世界", 1.0f) / 2.0f;
                textRenderer.renderText(guiRenderer, textX, WORLD_LIST_Y + 50.0f, "没有已保存的世界", 1.0f, new Vector4f(1.0f, 1.0f, 1.0f, 1.0f), windowWidth, windowHeight);
            }
            return;
        }

        float listX = windowWidth / 2.0f - 300.0f;
        float currentY = WORLD_LIST_Y;

        int visibleCount = (int) (WORLD_LIST_HEIGHT / WORLD_ITEM_HEIGHT);
        int startIndex = Math.max(0, scrollOffset);
        int endIndex = Math.min(worlds.size(), startIndex + visibleCount);

        for (int i = startIndex; i < endIndex; i++) {
            String worldName = worlds.get(i);
            boolean isSelected = worldName.equals(selectedWorld);
            
            Vector4f bgColor;
            if (isSelected) {
                bgColor = new Vector4f(0.3f, 0.5f, 0.8f, 0.9f);
            } else {
                bgColor = new Vector4f(0.2f, 0.2f, 0.2f, 0.9f);
            }

            guiRenderer.renderQuad(listX, currentY, 600.0f, WORLD_ITEM_HEIGHT, bgColor, windowWidth, windowHeight);

            TextRenderer textRenderer = guiRenderer.getTextRenderer();
            if (textRenderer != null) {
                textRenderer.renderText(guiRenderer, listX + 10.0f, currentY + 5.0f, worldName, 1.0f, new Vector4f(1.0f, 1.0f, 1.0f, 1.0f), windowWidth, windowHeight);
            }

            currentY += WORLD_ITEM_HEIGHT;
        }
    }

    public int handleInput(Input input, int windowWidth, int windowHeight) {
        Vector2d mousePos = input.getMousePosition();
        float centerX = windowWidth / 2.0f;

        float createWorldY = 50.0f;
        float backY = windowHeight - 60.0f;
        float buttonX = centerX - BUTTON_WIDTH / 2.0f;

        if (input.isMouseButtonPressed(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
            if (isMouseOverButton(mousePos.x, mousePos.y, buttonX, createWorldY, BUTTON_WIDTH, BUTTON_HEIGHT)) {
                return 1;
            }
            if (isMouseOverButton(mousePos.x, mousePos.y, buttonX, backY, BUTTON_WIDTH, BUTTON_HEIGHT)) {
                return 2;
            }

            List<String> saves = SaveManager.getInstance().getSaveList();
            float listX = windowWidth / 2.0f - 300.0f;
            float currentY = SAVE_LIST_Y;

            int startIndex = Math.max(0, saveScrollOffset);
            int endIndex = Math.min(saves.size(), startIndex + (int)(SAVE_LIST_HEIGHT / WORLD_ITEM_HEIGHT));

            for (int i = startIndex; i < endIndex; i++) {
                if (isMouseOverButton(mousePos.x, mousePos.y, listX, currentY, 600.0f, WORLD_ITEM_HEIGHT)) {
                    selectedSave = saves.get(i);
                    selectedWorld = null;
                    scrollOffset = 0;
                    return 0;
                }
                currentY += WORLD_ITEM_HEIGHT;
            }

            if (StringUtils.isNotBlank(selectedSave)) {
                List<String> worlds = WorldManager.getInstance().getWorldList(selectedSave);
                currentY = WORLD_LIST_Y;

                startIndex = Math.max(0, scrollOffset);
                endIndex = Math.min(worlds.size(), startIndex + (int)(WORLD_LIST_HEIGHT / WORLD_ITEM_HEIGHT));

                for (int i = startIndex; i < endIndex; i++) {
                    if (isMouseOverButton(mousePos.x, mousePos.y, listX, currentY, 600.0f, WORLD_ITEM_HEIGHT)) {
                        selectedWorld = worlds.get(i);
                        return 3;
                    }
                    currentY += WORLD_ITEM_HEIGHT;
                }
            }
        }

        double scrollY = input.getScrollY();
        if (scrollY != 0) {
            Vector2d mousePosition = input.getMousePosition();
            float listX = windowWidth / 2.0f - 300.0f;
            
            if (mousePosition.y >= SAVE_LIST_Y && mousePosition.y <= SAVE_LIST_Y + SAVE_LIST_HEIGHT) {
                List<String> saves = SaveManager.getInstance().getSaveList();
                int maxScroll = Math.max(0, saves.size() - (int)(SAVE_LIST_HEIGHT / WORLD_ITEM_HEIGHT));
                saveScrollOffset += (int)scrollY;
                if (saveScrollOffset < 0) {
                    saveScrollOffset = 0;
                }
                if (saveScrollOffset > maxScroll) {
                    saveScrollOffset = maxScroll;
                }
            } else if (StringUtils.isNotBlank(selectedSave) && mousePosition.y >= WORLD_LIST_Y && mousePosition.y <= WORLD_LIST_Y + WORLD_LIST_HEIGHT) {
                List<String> worlds = WorldManager.getInstance().getWorldList(selectedSave);
            int maxScroll = Math.max(0, worlds.size() - (int)(WORLD_LIST_HEIGHT / WORLD_ITEM_HEIGHT));
            scrollOffset += (int)scrollY;
            if (scrollOffset < 0) {
                scrollOffset = 0;
            }
            if (scrollOffset > maxScroll) {
                scrollOffset = maxScroll;
                }
            }
        }

        return 0;
    }

    private boolean isMouseOverButton(double mouseX, double mouseY, float buttonX, float buttonY, float buttonWidth, float buttonHeight) {
        return mouseX >= buttonX && mouseX <= buttonX + buttonWidth &&
               mouseY >= buttonY && mouseY <= buttonY + buttonHeight;
    }
}

