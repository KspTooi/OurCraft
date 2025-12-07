package com.ksptool.ourcraft.debug;

/**
 * 网格生成结果数据类，封装区块网格生成的结果数据
 */
public class DebugMeshGenerationResult {

    //区块坐标
    public final int chunkX;
    public final int chunkZ;

    //顶点坐标
    public final float[] vertices;

    //纹理坐标
    public final float[] texCoords;

    //颜色
    public final float[] tints;

    //动画数据
    public final float[] animationData;

    //索引
    public final int[] indices;

    //透明顶点坐标
    public final float[] transparentVertices;
    
    //透明纹理坐标
    public final float[] transparentTexCoords;

    //透明颜色
    public final float[] transparentTints;

    //透明动画数据
    public final float[] transparentAnimationData;

    //透明索引
    public final int[] transparentIndices;

    public DebugMeshGenerationResult(int chunkX, int chunkZ, float[] vertices, float[] texCoords, float[] tints, int[] indices) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.vertices = vertices;
        this.texCoords = texCoords;
        this.tints = tints;
        this.animationData = new float[0];
        this.indices = indices;
        this.transparentVertices = new float[0];
        this.transparentTexCoords = new float[0];
        this.transparentTints = new float[0];
        this.transparentAnimationData = new float[0];
        this.transparentIndices = new int[0];
    }

    public DebugMeshGenerationResult(int chunkX, int chunkZ, float[] vertices, float[] texCoords, float[] tints, float[] animationData, int[] indices,
                                     float[] transparentVertices, float[] transparentTexCoords, float[] transparentTints, float[] transparentAnimationData, int[] transparentIndices) {
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
}

