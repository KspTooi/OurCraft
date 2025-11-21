package com.ksptool.ourcraft.client.rendering;

import com.ksptool.ourcraft.sharedcore.item.Item;
import com.ksptool.ourcraft.sharedcore.blocks.inner.SharedBlock;
import com.ksptool.ourcraft.sharedcore.world.BlockState;
import com.ksptool.ourcraft.sharedcore.world.SharedWorld;
import com.ksptool.ourcraft.sharedcore.world.Registry;
import org.apache.commons.lang3.StringUtils;

/**
 * 物品渲染器类，负责渲染物品图标
 */
public class ItemRenderer {

    //GUI渲染器
    private GuiRenderer guiRenderer;

    //纹理图集ID
    private int textureAtlasId;

    //世界实例
    private SharedWorld world;

    public ItemRenderer(GuiRenderer guiRenderer, int textureAtlasId, SharedWorld world) {
        this.guiRenderer = guiRenderer;
        this.textureAtlasId = textureAtlasId;
        this.world = world;
    }

    public void renderItem(Item item, float x, float y, float size, int windowWidth, int windowHeight) {
        if (item == null) {
            return;
        }

        if (textureAtlasId == 0) {
            return;
        }

        String blockId = item.getBlockNamespacedID();
        if (StringUtils.isBlank(blockId)) {
            return;
        }

        Registry registry = Registry.getInstance();
        SharedBlock sharedBlock = registry.getBlock(blockId);
        if (sharedBlock == null) {
            return;
        }

        BlockState defaultState = sharedBlock.getDefaultState();
        String textureName = sharedBlock.getTextureName(world, 0, defaultState);
        if (StringUtils.isBlank(textureName)) {
            return;
        }

        TextureManager textureManager = TextureManager.getInstance();
        TextureManager.UVCoords uvCoords = textureManager.getUVCoords(textureName);
        if (uvCoords == null) {
            return;
        }

        org.joml.Vector4f color = new org.joml.Vector4f(1.0f, 1.0f, 1.0f, 1.0f);
        guiRenderer.renderTexturedQuad(x, y, size, size, 
            uvCoords.u0, uvCoords.v0, uvCoords.u1, uvCoords.v1, 
            color, textureAtlasId, windowWidth, windowHeight);
    }
}

