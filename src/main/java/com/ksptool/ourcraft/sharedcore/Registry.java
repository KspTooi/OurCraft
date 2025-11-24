package com.ksptool.ourcraft.sharedcore;

import com.ksptool.ourcraft.sharedcore.blocks.inner.SharedBlock;
import com.ksptool.ourcraft.sharedcore.entity.SharedEntity;
import com.ksptool.ourcraft.sharedcore.enums.BlockEnums;
import com.ksptool.ourcraft.sharedcore.enums.WorldTemplateEnums;
import com.ksptool.ourcraft.sharedcore.template.ItemTemplate;
import com.ksptool.ourcraft.sharedcore.world.WorldTemplate;
import lombok.extern.slf4j.Slf4j;
import java.util.HashMap;
import java.util.Map;

/**
 * 注册表，管理服务端和客户端上所有已注册的内容
 */
@Slf4j
public class Registry {

    private static final Registry INSTANCE = new Registry();

    private final Map<StdRegName, SharedBlock> blocks;

    private final Map<StdRegName, WorldTemplate> worldTemplates;

    private final Map<StdRegName, ItemTemplate> items;

    private final Map<StdRegName, SharedEntity> entities;

    private Registry() {
        this.blocks = new HashMap<>();
        this.worldTemplates = new HashMap<>();
        this.items = new HashMap<>();
        this.entities = new HashMap<>();
    }

    public static Registry getInstance() {
        return INSTANCE;
    }

    public void registerBlock(SharedBlock sharedBlock) {

        if (sharedBlock == null) {
            throw new IllegalArgumentException("SharedBlock is null!");
        }

        var stdRegName = sharedBlock.getStdRegName();

        if (blocks.containsKey(stdRegName)) {
            throw new IllegalArgumentException("Block with StdRegName " + stdRegName + " is already registered!");
        }

        log.info("注册方块: {}", stdRegName);
        blocks.put(stdRegName, sharedBlock);
    }

    public SharedBlock getBlock(String stdRegName) {
        return blocks.get(StdRegName.of(stdRegName));
    }

    public SharedBlock getBlock(StdRegName stdRegName) {
        return blocks.get(stdRegName);
    }


    public void registerWorldTemplate(WorldTemplate worldTemplate) {

        if (worldTemplate == null) {
            throw new IllegalArgumentException("WorldTemplate is null!");
        }

        var stdRegName = worldTemplate.getStdRegName();

        if (worldTemplates.containsKey(stdRegName)) {
            throw new IllegalArgumentException("WorldTemplate with StdRegName " + stdRegName + " is already registered!");
        }

        log.info("注册世界模板: {}", stdRegName);
        worldTemplates.put(stdRegName, worldTemplate);
    }

    public WorldTemplate getWorldTemplate(String stdRegName) {
        return worldTemplates.get(StdRegName.of(stdRegName));
    }

    public WorldTemplate getWorldTemplate(StdRegName stdRegName) {
        return worldTemplates.get(stdRegName);
    }

    public void registerItem(ItemTemplate itemTemplate) {
        if (itemTemplate == null) {
            throw new IllegalArgumentException("Item is null!");
        }
        log.info("注册物品: {}", itemTemplate.getStdRegName());
        items.put(itemTemplate.getStdRegName(), itemTemplate);
    }

    public ItemTemplate getItem(String stdRegName) {
        return items.get(StdRegName.of(stdRegName));
    }

    public ItemTemplate getItem(StdRegName stdRegName) {
        return items.get(stdRegName);
    }

    public void registerEntity(SharedEntity sharedEntity) {
        if (sharedEntity == null) {
            throw new IllegalArgumentException("EntityTemplate is null!");
        }
        log.info("注册实体: {}", sharedEntity.getStdRegName());
        entities.put(sharedEntity.getStdRegName(), sharedEntity);
    }

    public SharedEntity getEntity(String stdRegName) {
        return entities.get(StdRegName.of(stdRegName));
    }

    public SharedEntity getEntity(StdRegName stdRegName) {
        return entities.get(stdRegName);
    }

    public Map<StdRegName, SharedBlock> getAllBlocks() {
        return new HashMap<>(blocks);
    }

    /**
     * 注册所有引擎原版的内容 这包括方块、物品、世界模板、实体
     */
    public void registerAllDefaultContent() {
        BlockEnums.registerBlocks(this);
        WorldTemplateEnums.registerWorldTemplate(this);
    }

}

