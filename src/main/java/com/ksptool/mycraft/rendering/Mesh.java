package com.ksptool.mycraft.rendering;

import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.glDisableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.*;

/**
 * 网格数据管理类，封装顶点、纹理坐标、索引等渲染数据
 */
public class Mesh {
    private int vaoId;
    private int vboId;
    private int eboId;
    private int vertexCount;

    public Mesh(float[] vertices, float[] texCoords, int[] indices) {
        this(vertices, texCoords, new float[texCoords.length / 2], new float[texCoords.length], indices);
    }

    public Mesh(float[] vertices, float[] texCoords, float[] tints, int[] indices) {
        this(vertices, texCoords, tints, new float[texCoords.length], indices);
    }

    public Mesh(float[] vertices, float[] texCoords, float[] tints, float[] animationData, int[] indices) {
        vertexCount = indices.length;

        vaoId = glGenVertexArrays();
        glBindVertexArray(vaoId);

        vboId = glGenBuffers();
        FloatBuffer verticesBuffer = MemoryUtil.memAllocFloat(vertices.length + texCoords.length + tints.length + animationData.length);
        verticesBuffer.put(vertices);
        verticesBuffer.put(texCoords);
        verticesBuffer.put(tints);
        verticesBuffer.put(animationData);
        verticesBuffer.flip();

        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, verticesBuffer, GL_STATIC_DRAW);
        MemoryUtil.memFree(verticesBuffer);

        glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);
        glEnableVertexAttribArray(0);
        
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 0, vertices.length * 4);
        glEnableVertexAttribArray(1);
        
        glVertexAttribPointer(2, 1, GL_FLOAT, false, 0, (vertices.length + texCoords.length) * 4);
        glEnableVertexAttribArray(2);

        glVertexAttribPointer(3, 3, GL_FLOAT, false, 0, (vertices.length + texCoords.length + tints.length) * 4);
        glEnableVertexAttribArray(3);

        eboId = glGenBuffers();
        IntBuffer indicesBuffer = MemoryUtil.memAllocInt(indices.length);
        indicesBuffer.put(indices);
        indicesBuffer.flip();

        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, eboId);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indicesBuffer, GL_STATIC_DRAW);
        MemoryUtil.memFree(indicesBuffer);

        glBindVertexArray(0);
    }

    public void render() {
        if (vaoId == 0) {
            return;
        }
        if (vertexCount == 0) {
            return;
        }
        glBindVertexArray(vaoId);
        glDrawElements(GL_TRIANGLES, vertexCount, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
    }

    public void cleanup() {
        glDisableVertexAttribArray(0);
        glDisableVertexAttribArray(1);
        glDisableVertexAttribArray(2);
        glDisableVertexAttribArray(3);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glDeleteBuffers(vboId);
        glDeleteBuffers(eboId);

        glBindVertexArray(0);
        glDeleteVertexArrays(vaoId);
    }
}

