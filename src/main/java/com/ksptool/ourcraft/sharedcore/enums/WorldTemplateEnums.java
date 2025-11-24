package com.ksptool.ourcraft.sharedcore.enums;

import com.ksptool.ourcraft.sharedcore.StdRegName;
import com.ksptool.ourcraft.sharedcore.Registry;
import com.ksptool.ourcraft.sharedcore.world.WorldTemplate;
import com.ksptool.ourcraft.sharedcore.world.WorldTemplateEarthLike;
import lombok.Getter;
import lombok.SneakyThrows;

/**
 * 世界模板类型枚举，管理所有世界模板的命名空间ID和注册逻辑
 */
@Getter
public enum WorldTemplateEnums {

    EARTH_LIKE(StdRegName.of("ourcraft:earth_like"), WorldTemplateEarthLike.class);

    private final StdRegName stdRegName;
    
    private final Class<? extends WorldTemplate> templateClass;

    WorldTemplateEnums(StdRegName stdRegName, Class<? extends WorldTemplate> templateClass) {
        if(stdRegName == null){
            throw new IllegalArgumentException("StdRegName is null!");
        }
        this.stdRegName = stdRegName;
        this.templateClass = templateClass;
    }

    /**
     * 创建世界模板实例
     * @return 世界模板实例
     */
    @SneakyThrows
    public WorldTemplate createInstance() {
        return this.templateClass.getConstructor().newInstance();
    }

    /**
     * 注册所有引擎自带的默认世界模板
     * @param registry 注册表
     */
    public static void registerWorldTemplate(Registry registry) {
        for (WorldTemplateEnums type : values()) {
            registry.registerWorldTemplate(type.createInstance());
        }
    }
}

