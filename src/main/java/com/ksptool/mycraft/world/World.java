package com.ksptool.mycraft.world;

import com.ksptool.mycraft.server.world.ServerChunk;
import com.ksptool.mycraft.server.world.ServerWorld;
import com.ksptool.mycraft.sharedcore.BoundingBox;
import com.ksptool.mycraft.world.save.RegionManager;
import com.ksptool.mycraft.world.gen.GenerationContext;
import com.ksptool.mycraft.world.gen.TerrainPipeline;
import com.ksptool.mycraft.world.gen.layers.BaseDensityLayer;
import com.ksptool.mycraft.world.gen.layers.FeatureLayer;
import com.ksptool.mycraft.world.gen.layers.SurfaceLayer;
import com.ksptool.mycraft.world.gen.layers.WaterLayer;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

import java.util.List;

/**
 * 世界管理类，负责世界模拟逻辑的协调
 * 注意：此类继承自 ServerWorld 以保持向后兼容性
 */
@Getter
public class World extends ServerWorld {
    /**
     * 世界管理类，负责世界模拟逻辑的协调
     * 注意：此类继承自 ServerWorld 以保持向后兼容性
     * 所有功能都委托给父类 ServerWorld
     */
    public World(WorldTemplate template) {
        super(template);
    }
}
