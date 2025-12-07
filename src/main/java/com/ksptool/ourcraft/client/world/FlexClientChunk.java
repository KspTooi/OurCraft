package com.ksptool.ourcraft.client.world;

import com.ksptool.ourcraft.client.rendering.Mesh;
import com.ksptool.ourcraft.sharedcore.BoundingBox;
import com.ksptool.ourcraft.sharedcore.GlobalPalette;
import com.ksptool.ourcraft.sharedcore.utils.FlexChunkData;
import com.ksptool.ourcraft.sharedcore.world.BlockState;
import lombok.Getter;

/**
 * 客户端Flex区块，使用FlexChunkData存储渲染相关的数据
 */
@Getter
public class FlexClientChunk {
    
    //区块大小（与ClientChunk保持一致）
    public static final int CHUNK_SIZE = 16;
    
    private final int chunkX;
    private final int chunkZ;
    
    private FlexChunkData flexChunkData;
    private Mesh mesh;
    private Mesh transparentMesh;
    private BoundingBox boundingBox;
    private boolean needsMeshUpdate = false;
    
    public FlexClientChunk(int chunkX, int chunkZ) {
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
    
    public void setMesh(Mesh mesh) {
        if (this.mesh != null) {
            this.mesh.cleanup();
        }
        this.mesh = mesh;
        this.needsMeshUpdate = false;
    }
    
    public void setTransparentMesh(Mesh transparentMesh) {
        if (this.transparentMesh != null) {
            this.transparentMesh.cleanup();
        }
        this.transparentMesh = transparentMesh;
    }
    
    public boolean hasMesh() {
        return mesh != null;
    }
    
    public boolean hasTransparentMesh() {
        return transparentMesh != null;
    }
    
    public void markNeedsMeshUpdate() {
        this.needsMeshUpdate = true;
    }
    
    public boolean needsMeshUpdate() {
        return needsMeshUpdate;
    }
    
    public void render() {
        if (mesh != null) {
            mesh.render();
        }
    }
    
    public void renderTransparent() {
        if (transparentMesh != null) {
            transparentMesh.render();
        }
    }
    
    public void cleanup() {
        if (mesh != null) {
            mesh.cleanup();
            mesh = null;
        }
        if (transparentMesh != null) {
            transparentMesh.cleanup();
            transparentMesh = null;
        }
        flexChunkData = null;
    }
}
