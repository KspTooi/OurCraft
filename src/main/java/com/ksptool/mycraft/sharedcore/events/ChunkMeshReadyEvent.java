package com.ksptool.mycraft.sharedcore.events;

/**
 * 区块网格就绪事件，当区块网格生成完成时触发
 * 
 * 【里程碑/历史记录】
 * 此事件已被废弃，仅作为历史记录保留。
 * 原因：服务端向客户端发送顶点、纹理数据是不合理的架构设计。
 * 当前架构：服务端只发送方块数据（ChunkDataEvent），客户端负责生成网格。
 * 
 * @deprecated 已废弃，请使用 ChunkDataEvent
 */
@Deprecated
public class ChunkMeshReadyEvent extends GameEvent {
    private final int chunkX;
    private final int chunkZ;
    private final float[] vertices;
    private final float[] texCoords;
    private final float[] tints;
    private final float[] animationData;
    private final int[] indices;
    private final float[] transparentVertices;
    private final float[] transparentTexCoords;
    private final float[] transparentTints;
    private final float[] transparentAnimationData;
    private final int[] transparentIndices;
    
    public ChunkMeshReadyEvent(int chunkX, int chunkZ,
                               float[] vertices, float[] texCoords, float[] tints, float[] animationData, int[] indices,
                               float[] transparentVertices, float[] transparentTexCoords, float[] transparentTints,
                               float[] transparentAnimationData, int[] transparentIndices) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.vertices = vertices;
        this.texCoords = texCoords;
        this.tints = tints;
        this.animationData = animationData;
        this.indices = indices;
        this.transparentVertices = transparentVertices;
        this.transparentTexCoords = transparentTexCoords;
        this.transparentTints = transparentTints;
        this.transparentAnimationData = transparentAnimationData;
        this.transparentIndices = transparentIndices;
    }
    
    public int getChunkX() {
        return chunkX;
    }
    
    public int getChunkZ() {
        return chunkZ;
    }
    
    public float[] getVertices() {
        return vertices;
    }
    
    public float[] getTexCoords() {
        return texCoords;
    }
    
    public float[] getTints() {
        return tints;
    }
    
    public float[] getAnimationData() {
        return animationData;
    }
    
    public int[] getIndices() {
        return indices;
    }
    
    public float[] getTransparentVertices() {
        return transparentVertices;
    }
    
    public float[] getTransparentTexCoords() {
        return transparentTexCoords;
    }
    
    public float[] getTransparentTints() {
        return transparentTints;
    }
    
    public float[] getTransparentAnimationData() {
        return transparentAnimationData;
    }
    
    public int[] getTransparentIndices() {
        return transparentIndices;
    }
}

