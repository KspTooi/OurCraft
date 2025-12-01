package com.ksptool.ourcraft.client.rendering;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 着色器程序管理类，负责加载、编译和链接GLSL着色器程序
 */
@Slf4j
public class ShaderProgram {

    //程序ID
    private int programId;

    //顶点着色器ID
    private int vertexShaderId;

    //片元着色器ID
    private int fragmentShaderId;

    //加载着色器
    public ShaderProgram(String vertexShaderPath, String fragmentShaderPath) {
        vertexShaderId = loadShader(vertexShaderPath, GL20.GL_VERTEX_SHADER);
        fragmentShaderId = loadShader(fragmentShaderPath, GL20.GL_FRAGMENT_SHADER);
        programId = GL20.glCreateProgram();
        GL20.glAttachShader(programId, vertexShaderId);
        GL20.glAttachShader(programId, fragmentShaderId);
        GL20.glLinkProgram(programId);

        if (GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) == 0) {
            String error = GL20.glGetProgramInfoLog(programId);
            log.error("Shader linking error: {}", error);
            throw new RuntimeException("Error linking Shader code: " + error);
        }

        if (vertexShaderId != 0) {
            GL20.glDetachShader(programId, vertexShaderId);
        }
        if (fragmentShaderId != 0) {
            GL20.glDetachShader(programId, fragmentShaderId);
        }
    }

    private int loadShader(String shaderPath, int shaderType) {
        StringBuilder shaderSource = new StringBuilder();
        try (InputStream in = getClass().getResourceAsStream(shaderPath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                shaderSource.append(line).append("\n");
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not load shader: " + shaderPath, e);
        }

        int shaderId = GL20.glCreateShader(shaderType);
        if (shaderId == 0) {
            throw new RuntimeException("Error creating shader. Type: " + shaderType);
        }

        GL20.glShaderSource(shaderId, shaderSource);
        GL20.glCompileShader(shaderId);

        if (GL20.glGetShaderi(shaderId, GL20.GL_COMPILE_STATUS) == 0) {
            throw new RuntimeException("Error compiling Shader code: " + GL20.glGetShaderInfoLog(shaderId));
        }

        return shaderId;
    }

    public void bind() {
        GL20.glUseProgram(programId);
    }

    public void unbind() {
        GL20.glUseProgram(0);
    }

    public void cleanup() {
        unbind();
        if (programId != 0) {
            GL30.glDeleteProgram(programId);
        }
    }

    public int getProgramId() {
        return programId;
    }

    public void setUniform(String name, int value) {
        int location = GL20.glGetUniformLocation(programId, name);
        if (location == -1) {
            log.warn("Uniform '{}' not found in shader!", name);
            return;
        }
        GL20.glUniform1i(location, value);
    }

    public void setUniform(String name, float value) {
        int location = GL20.glGetUniformLocation(programId, name);
        if (location == -1) {
            log.warn("Uniform '{}' not found in shader!", name);
            return;
        }
        GL20.glUniform1f(location, value);
    }

    public void setUniform(String name, Matrix4f value) {
        int location = GL20.glGetUniformLocation(programId, name);
        if (location == -1) {
            log.warn("Uniform '{}' not found in shader!", name);
            return;
        }
        float[] matrixArray = new float[16];
        value.get(matrixArray);
        GL20.glUniformMatrix4fv(location, false, matrixArray);
    }

    public void setUniform(String name, org.joml.Vector3f value) {
        int location = GL20.glGetUniformLocation(programId, name);
        if (location == -1) {
            log.warn("Uniform '{}' not found in shader!", name);
            return;
        }
        GL20.glUniform3f(location, value.x, value.y, value.z);
    }

    public void setUniform(String name, org.joml.Vector4f value) {
        int location = GL20.glGetUniformLocation(programId, name);
        if (location == -1) {
            log.warn("Uniform '{}' not found in shader!", name);
            return;
        }
        GL20.glUniform4f(location, value.x, value.y, value.z, value.w);
    }

    public void setUniform(String name, org.joml.Vector2f value) {
        int location = GL20.glGetUniformLocation(programId, name);
        if (location == -1) {
            log.warn("Uniform '{}' not found in shader!", name);
            return;
        }
        GL20.glUniform2f(location, value.x, value.y);
    }

    public void setUniform(String name, boolean value) {
        int location = GL20.glGetUniformLocation(programId, name);
        if (location == -1) {
            log.warn("Uniform '{}' not found in shader!", name);
            return;
        }
        GL20.glUniform1i(location, value ? 1 : 0);
    }

    public void setAmbientLight(org.joml.Vector3f lightColor) {
        setUniform("ambientLight", lightColor);
    }
}

