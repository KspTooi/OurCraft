package com.ksptool.ourcraft.sharedcore.world;

import com.ksptool.ourcraft.sharedcore.block.SharedBlock;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 方块注册表类，管理所有已注册的方块和世界模板
 */
@Slf4j
public class Registry {

    //实例
    private static final Registry INSTANCE = new Registry();

    //方块列表
    private final Map<String, SharedBlock> blocks;
    
    //世界模板列表
    private static final Map<String, WorldTemplate> worldTemplateRegistry = new HashMap<>();

    private Registry() {
        this.blocks = new HashMap<>();
    }

    public static Registry getInstance() {
        return INSTANCE;
    }

    public void register(SharedBlock sharedBlock) {
        String id = sharedBlock.getNamespacedID();
        if (blocks.containsKey(id)) {
            throw new IllegalArgumentException("Block with ID " + id + " is already registered!");
        }
        blocks.put(id, sharedBlock);
    }

    public SharedBlock get(String namespacedID) {
        return blocks.get(namespacedID);
    }

    public Map<String, SharedBlock> getAllBlocks() {
        return new HashMap<>(blocks);
    }

    public void clear() {
        blocks.clear();
    }
    
    public static void registerWorldTemplate(WorldTemplate template) {
        if (template == null || StringUtils.isBlank(template.getTemplateId())) {
            log.warn("尝试注册无效的世界模板");
            return;
        }
        worldTemplateRegistry.put(template.getTemplateId(), template);
        log.debug("注册世界模板: {}", template.getTemplateId());
    }
    
    public static WorldTemplate getWorldTemplate(String templateId) {
        return worldTemplateRegistry.get(templateId);
    }
    
    public static WorldTemplate getDefaultTemplate() {
        WorldTemplate template = worldTemplateRegistry.get("mycraft:overworld");
        if (template == null) {
            log.warn("默认世界模板 'mycraft:overworld' 未找到，请确保在游戏启动时注册");
        }
        return template;
    }
}

