package com.ksptool.ourcraft.sharedcore.world;

import com.ksptool.ourcraft.sharedcore.StdRegName;
import com.ksptool.ourcraft.sharedcore.blocks.inner.SharedBlock;
import com.ksptool.ourcraft.sharedcore.entity.SharedEntity;
import com.ksptool.ourcraft.sharedcore.template.ItemTemplate;
import com.ksptool.ourcraft.sharedcore.template.WorldTemplate;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

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

    private static final Map<String, WorldTemplateOld> worldTemplateRegistry = new HashMap<>();

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
        entities.put(sharedEntity.getStdRegName(), sharedEntity);
    }

    public SharedEntity getEntity(String stdRegName) {
        return entities.get(StdRegName.of(stdRegName));
    }

    public SharedEntity getEntity(StdRegName stdRegName) {
        return entities.get(stdRegName);
    }


    public static void registerWorldTemplateOld(WorldTemplateOld template) {
        if (template == null || StringUtils.isBlank(template.getTemplateId())) {
            log.warn("尝试注册无效的世界模板");
            return;
        }
        worldTemplateRegistry.put(template.getTemplateId(), template);
        log.debug("注册世界模板: {}", template.getTemplateId());
    }

    public static WorldTemplateOld getWorldTemplateOld(String templateId) {
        return worldTemplateRegistry.get(templateId);
    }

    public static WorldTemplateOld getDefaultTemplate() {
        WorldTemplateOld template = worldTemplateRegistry.get("mycraft:overworld");
        if (template == null) {
            log.warn("默认世界模板 'mycraft:overworld' 未找到，请确保在游戏启动时注册");
        }
        return template;
    }


    public Map<StdRegName, SharedBlock> getAllBlocks() {
        return new HashMap<>(blocks);
    }
}

