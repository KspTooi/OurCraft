package com.ksptool.ourcraft.sharedcore.utils.viewport;

import java.util.HashSet;
import java.util.Set;
import com.ksptool.ourcraft.sharedcore.utils.position.ChunkPos;
import lombok.Getter;

/**
 * 视口类,负责计算视口相关逻辑
 * 视口是Player能够看到的区域,通常是一个矩形,Player可以在这个区域内移动
 */
@Getter
public class ChunkViewPort {

    //视口距离
    @Getter
    private final int viewDistance;

    //原点
    @Getter
    private final ChunkPos center;

    //视口模式 0:矩形视口,1:圆形视口
    private int mode = 0;

    private static final int MODE_RECTANGLE = 0;
    private static final int MODE_CIRCLE = 1;


    private ChunkViewPort(ChunkPos center,int viewDistance) {
        this.viewDistance = viewDistance;
        this.center = center;
    }

    /**
     * 创建一个区块视口
     * @param center 中心区块
     * @param viewDistance 视口距离
     * @return 区块视口
     */
    public static ChunkViewPort of(ChunkPos center,int viewDistance) {
        return new ChunkViewPort(center, viewDistance);
    }

    /**
     * 判断一个区块是否在视口内
     * @param otherChunkPos 其他区块
     * @return 是否在视口内
     */
    public boolean contains(ChunkPos otherChunkPos) {
        if(otherChunkPos == null){
            return false;
        }

        if (mode == MODE_RECTANGLE) {
            int dx = Math.abs(otherChunkPos.getX() - center.getX());
            int dz = Math.abs(otherChunkPos.getZ() - center.getZ());
            return dx <= viewDistance && dz <= viewDistance;
        }

        if (mode == MODE_CIRCLE) {
            int dx = otherChunkPos.getX() - center.getX();
            int dz = otherChunkPos.getZ() - center.getZ();
            int distanceSquared = dx * dx + dz * dz;
            int radiusSquared = viewDistance * viewDistance;
            return distanceSquared <= radiusSquared;
        }

        return false;
    }

    /**
     * 获取视口内的所有区域坐标
     * @return 视口内的所有区域坐标
     */
    public Set<ChunkPos> getChunkPosSet() {
        var chunkPosSet = new HashSet<ChunkPos>();

        if (mode == MODE_RECTANGLE) {
            var xStart = center.getX() - viewDistance;
            var zStart = center.getZ() - viewDistance;
            var xEnd = center.getX() + viewDistance;
            var zEnd = center.getZ() + viewDistance;

            for(var x = xStart; x <= xEnd; x++) {
                for(var z = zStart; z <= zEnd; z++) {
                    chunkPosSet.add(ChunkPos.of(x, 0, z));
                }
            }
            return chunkPosSet;
        }

        if (mode == MODE_CIRCLE) {
            var xStart = center.getX() - viewDistance;
            var zStart = center.getZ() - viewDistance;
            var xEnd = center.getX() + viewDistance;
            var zEnd = center.getZ() + viewDistance;
            int radiusSquared = viewDistance * viewDistance;

            for(var x = xStart; x <= xEnd; x++) {
                for(var z = zStart; z <= zEnd; z++) {
                    int dx = x - center.getX();
                    int dz = z - center.getZ();
                    int distanceSquared = dx * dx + dz * dz;
                    if (distanceSquared <= radiusSquared) {
                        chunkPosSet.add(ChunkPos.of(x, 0, z));
                    }
                }
            }
            return chunkPosSet;
        }

        return chunkPosSet;
    }


    /**
     * 设置视口模式
     * @param mode 视口模式 0:矩形视口,1:圆形视口
     */
    public void setMode(int mode) {
        if(mode != MODE_RECTANGLE && mode != MODE_CIRCLE){
            throw new IllegalArgumentException("视口模式错误,mode只能为0或1");
        }
        this.mode = mode;
    }
}
