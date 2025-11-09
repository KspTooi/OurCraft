package com.ksptool.mycraft.world;

/**
 * 网格生成结果数据类，封装区块网格生成的结果数据
 */
public class MeshGenerationResult {
    public final float[] vertices;
    public final float[] texCoords;
    public final float[] tints;
    public final float[] animationData;
    public final int[] indices;
    public final float[] transparentVertices;
    public final float[] transparentTexCoords;
    public final float[] transparentTints;
    public final float[] transparentAnimationData;
    public final int[] transparentIndices;
    public final Chunk chunk;

    public MeshGenerationResult(Chunk chunk, float[] vertices, float[] texCoords, float[] tints, int[] indices) {
        this.chunk = chunk;
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

    public MeshGenerationResult(Chunk chunk, float[] vertices, float[] texCoords, float[] tints, float[] animationData, int[] indices,
                                 float[] transparentVertices, float[] transparentTexCoords, float[] transparentTints, float[] transparentAnimationData, int[] transparentIndices) {
        this.chunk = chunk;
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

