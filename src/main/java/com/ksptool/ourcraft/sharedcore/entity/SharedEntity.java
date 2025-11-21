package com.ksptool.ourcraft.sharedcore.entity;

import com.ksptool.ourcraft.sharedcore.StdRegName;
import com.ksptool.ourcraft.sharedcore.entity.components.EntityComponent;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 定义实体的基本属性
 */
public abstract class SharedEntity {

    @Getter
    private final StdRegName stdRegName;

    @Getter
    private final UUID uuid;

    @Getter
    private boolean markedForRemoval = false;

    private final Map<Class<? extends EntityComponent>, EntityComponent> components = new HashMap<>();

    public SharedEntity(StdRegName stdRegName) {
        this.stdRegName = stdRegName;
        this.uuid = UUID.randomUUID();
    }

    public SharedEntity(StdRegName stdRegName, UUID uuid) {
        this.stdRegName = stdRegName;
        this.uuid = uuid;
    }

    public <T extends EntityComponent> void addComponent(T component) {
        components.put(component.getClass(), component);
    }

    public <T extends EntityComponent> T getComponent(Class<T> type) {
        return type.cast(components.get(type));
    }

    public <T extends EntityComponent> boolean hasComponent(Class<T> type) {
        return components.containsKey(type);
    }

    public void remove() {
        this.markedForRemoval = true;
    }

}
