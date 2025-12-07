package com.ksptool.ourcraft.debug;

import com.jme3.scene.Geometry;
import com.ksptool.ourcraft.sharedcore.BoundingBox;
import com.ksptool.ourcraft.sharedcore.GlobalPalette;
import com.ksptool.ourcraft.sharedcore.utils.FlexChunkData;
import com.ksptool.ourcraft.sharedcore.world.BlockState;
import lombok.Getter;

/**
 * JME客户端Flex区块，使用FlexChunkData存储渲染相关的数据
 */
@Getter
public class DebugFlexClientChunk {
    
    //区块大小（与ClientChunk保持一致）
    public static final int CHUNK_SIZE = 16;
    
    private final int chunkX;
    private final int chunkZ;
    
    private FlexChunkData flexChunkData;
    private Geometry geometry;
    private Geometry transparentGeometry;
    private BoundingBox boundingBox;
    private boolean needsMeshUpdate = false;
    
    public DebugFlexClientChunk(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        
        float minX = chunkX * CHUNK_SIZE;
        float maxX = minX + CHUNK_SIZE;
        float minZ = chunkZ * CHUNK_SIZE;
        float maxZ = minZ + CHUNK_SIZE;
        
        // 初始包围盒高度设置为0，待设置数据后更新，或者设为一个合理的默认值
        // 这里暂时设为0，setFlexChunkData时会更新
        this.boundingBox = new BoundingBox(minX, 0, minZ, maxX, 0, maxZ);
    }
    
    /**
     * 设置Flex区块数据（通常来自网络）
     */
    public void setFlexChunkData(FlexChunkData flexChunkData) {
        if (flexChunkData == null) {
            throw new IllegalArgumentException("FlexChunkData不能为空");
        }
        this.flexChunkData = flexChunkData;
        this.needsMeshUpdate = true;
        
        // 更新包围盒高度
        double minX = boundingBox.getMinX();
        double maxX = boundingBox.getMaxX();
        double minZ = boundingBox.getMinZ();
        double maxZ = boundingBox.getMaxZ();
        this.boundingBox = new BoundingBox(minX, 0, minZ, maxX, flexChunkData.getHeight(), maxZ);
    }
    
    /**
     * 线程安全地获取方块状态
     * 直接调用flexChunkData.getBlock，该方法自带锁
     */
    public BlockState getBlockState(int x, int y, int z) {
        if (flexChunkData == null) {
            return GlobalPalette.getInstance().getState(0);
        }
        if (x < 0 || x >= CHUNK_SIZE || y < 0 || y >= flexChunkData.getHeight() || z < 0 || z >= CHUNK_SIZE) {
            return GlobalPalette.getInstance().getState(0);
        }
        return flexChunkData.getBlock(x, y, z);
    }
    
    /**
     * 获取方块状态ID（用于兼容旧代码）
     */
    public int getBlockStateId(int x, int y, int z) {
        BlockState state = getBlockState(x, y, z);
        return GlobalPalette.getInstance().getStateId(state);
    }
    
    /**
     * 设置方块状态
     */
    public void setBlockState(int x, int y, int z, BlockState state) {
        if (flexChunkData == null) {
            throw new IllegalStateException("FlexChunkData未初始化，无法设置方块状态");
        }
        if (x < 0 || x >= CHUNK_SIZE || y < 0 || y >= flexChunkData.getHeight() || z < 0 || z >= CHUNK_SIZE) {
            return;
        }
        flexChunkData.setBlock(x, y, z, state);
        needsMeshUpdate = true;
    }
    
    /**
     * 创建数据快照用于异步网格生成
     * 快照是线程安全的，无锁读取
     */
    public FlexChunkData.Snapshot createSnapshot() {
        if (flexChunkData == null) {
            throw new IllegalStateException("FlexChunkData未初始化，无法创建快照");
        }
        return flexChunkData.createSnapshot();
    }
    
    public void setGeometry(Geometry geometry) {
        if (this.geometry != null) {
            this.geometry.removeFromParent();
        }
        this.geometry = geometry;
        this.needsMeshUpdate = false;
    }
    
    public void setTransparentGeometry(Geometry transparentGeometry) {
        if (this.transparentGeometry != null) {
            this.transparentGeometry.removeFromParent();
        }
        this.transparentGeometry = transparentGeometry;
    }
    
    public boolean hasGeometry() {
        return geometry != null;
    }
    
    public boolean hasTransparentGeometry() {
        return transparentGeometry != null;
    }
    
    public void markNeedsMeshUpdate() {
        this.needsMeshUpdate = true;
    }
    
    public boolean needsMeshUpdate() {
        return needsMeshUpdate;
    }
    
    public void cleanup() {
        if (geometry != null) {
            geometry.removeFromParent();
            geometry = null;
        }
        if (transparentGeometry != null) {
            transparentGeometry.removeFromParent();
            transparentGeometry = null;
        }
        flexChunkData = null;
    }
}
