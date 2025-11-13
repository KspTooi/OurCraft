package com.ksptool.mycraft.rendering;

import com.ksptool.mycraft.entity.Player;
import org.joml.Vector4f;

/**
 * HUD渲染器类，负责渲染游戏内HUD元素（十字准星、生命条、饱食度条、快捷栏）
 */
public class HudRenderer {

    //GUI渲染器
    private GuiRenderer guiRenderer;

    //文本渲染器
    private TextRenderer textRenderer;

    //快捷栏渲染器
    private HotbarRenderer hotbarRenderer;

    //物品渲染器
    private ItemRenderer itemRenderer;

    public HudRenderer(GuiRenderer guiRenderer, int textureAtlasId) {
        this.guiRenderer = guiRenderer;
        this.textRenderer = guiRenderer.getTextRenderer();
        this.hotbarRenderer = new HotbarRenderer();
        this.itemRenderer = new ItemRenderer(guiRenderer, textureAtlasId);
        this.hotbarRenderer.setItemRenderer(itemRenderer);
    }

    public void render(Player player, int windowWidth, int windowHeight) {
        if (player == null) {
            return;
        }

        renderCrosshair(windowWidth, windowHeight);
        renderHealthBar(player, windowWidth, windowHeight);
        renderHungerBar(player, windowWidth, windowHeight);
        hotbarRenderer.render(player, windowWidth, windowHeight);
    }

    private void renderCrosshair(int windowWidth, int windowHeight) {
        float centerX = windowWidth / 2.0f;
        float centerY = windowHeight / 2.0f;
        float crosshairSize = 20.0f;
        float crosshairThickness = 2.0f;

        Vector4f crosshairColor = new Vector4f(1.0f, 1.0f, 1.0f, 0.8f);

        guiRenderer.renderQuad(centerX - crosshairThickness / 2, centerY - crosshairSize / 2, 
            crosshairThickness, crosshairSize, crosshairColor, windowWidth, windowHeight);
        guiRenderer.renderQuad(centerX - crosshairSize / 2, centerY - crosshairThickness / 2, 
            crosshairSize, crosshairThickness, crosshairColor, windowWidth, windowHeight);
    }

    private void renderHealthBar(Player player, int windowWidth, int windowHeight) {
        float barWidth = 200.0f;
        float barHeight = 24.0f;
        float barX = 20.0f;
        float barY = windowHeight - 80.0f;

        float health = player.getHealth();
        float maxHealth = player.getMaxHealth();
        float healthRatio = Math.max(0.0f, Math.min(1.0f, health / maxHealth));

        Vector4f backgroundColor = new Vector4f(0.2f, 0.2f, 0.2f, 0.7f);
        guiRenderer.renderQuad(barX, barY, barWidth, barHeight, backgroundColor, windowWidth, windowHeight);

        Vector4f healthColor = new Vector4f(0.8f, 0.2f, 0.2f, 0.9f);
        float healthBarWidth = barWidth * healthRatio;
        guiRenderer.renderQuad(barX, barY, healthBarWidth, barHeight, healthColor, windowWidth, windowHeight);

        Vector4f borderColor = new Vector4f(0.0f, 0.0f, 0.0f, 1.0f);
        float borderWidth = 2.0f;
        guiRenderer.renderQuad(barX, barY, barWidth, borderWidth, borderColor, windowWidth, windowHeight);
        guiRenderer.renderQuad(barX, barY + barHeight - borderWidth, barWidth, borderWidth, borderColor, windowWidth, windowHeight);
        guiRenderer.renderQuad(barX, barY, borderWidth, barHeight, borderColor, windowWidth, windowHeight);
        guiRenderer.renderQuad(barX + barWidth - borderWidth, barY, borderWidth, barHeight, borderColor, windowWidth, windowHeight);

        String healthText = String.format("%.0f/%.0f", health, maxHealth);
        Vector4f textColor = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);
        float textScale = 0.5f;
        float textX = barX + 5.0f;
        float textY = barY + (barHeight - 16.0f * textScale) / 2.0f;
        textRenderer.renderText(guiRenderer, textX, textY, healthText, textScale, textColor, windowWidth, windowHeight);
    }

    private void renderHungerBar(Player player, int windowWidth, int windowHeight) {
        float barWidth = 200.0f;
        float barHeight = 24.0f;
        float barX = 20.0f;
        float barY = windowHeight - 110.0f;

        float hunger = player.getHunger();
        float maxHunger = player.getMaxHunger();
        float hungerRatio = Math.max(0.0f, Math.min(1.0f, hunger / maxHunger));

        Vector4f backgroundColor = new Vector4f(0.2f, 0.2f, 0.2f, 0.7f);
        guiRenderer.renderQuad(barX, barY, barWidth, barHeight, backgroundColor, windowWidth, windowHeight);

        Vector4f hungerColor = new Vector4f(0.9f, 0.7f, 0.2f, 0.9f);
        float hungerBarWidth = barWidth * hungerRatio;
        guiRenderer.renderQuad(barX, barY, hungerBarWidth, barHeight, hungerColor, windowWidth, windowHeight);

        Vector4f borderColor = new Vector4f(0.0f, 0.0f, 0.0f, 1.0f);
        float borderWidth = 2.0f;
        guiRenderer.renderQuad(barX, barY, barWidth, borderWidth, borderColor, windowWidth, windowHeight);
        guiRenderer.renderQuad(barX, barY + barHeight - borderWidth, barWidth, borderWidth, borderColor, windowWidth, windowHeight);
        guiRenderer.renderQuad(barX, barY, borderWidth, barHeight, borderColor, windowWidth, windowHeight);
        guiRenderer.renderQuad(barX + barWidth - borderWidth, barY, borderWidth, barHeight, borderColor, windowWidth, windowHeight);

        String hungerText = String.format("%.0f/%.0f", hunger, maxHunger);
        Vector4f textColor = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);
        float textScale = 0.5f;
        float textX = barX + 5.0f;
        float textY = barY + (barHeight - 16.0f * textScale) / 2.0f;
        textRenderer.renderText(guiRenderer, textX, textY, hungerText, textScale, textColor, windowWidth, windowHeight);
    }

    public void cleanup() {
        if (hotbarRenderer != null) {
            hotbarRenderer.cleanup();
        }
    }
}

