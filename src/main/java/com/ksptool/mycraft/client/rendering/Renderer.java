package com.ksptool.mycraft.client.rendering;

import com.ksptool.mycraft.client.entity.ClientPlayer;

import lombok.extern.slf4j.Slf4j;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * 主渲染器类，负责渲染世界和玩家快捷栏
 */
@Slf4j
public class Renderer {
    
    //着色器程序
    private ShaderProgram shader;

    //投影矩阵
    private Matrix4f projectionMatrix;

    //快捷栏渲染器
    private HotbarRenderer hotbarRenderer;

    //HUD渲染器
    private HudRenderer hudRenderer;

    //开始时间
    private double startTimeSec;

    public void init() {
        shader = new ShaderProgram("/shaders/vertex.glsl", "/shaders/fragment.glsl");
        projectionMatrix = new Matrix4f();
        hotbarRenderer = new HotbarRenderer();
        startTimeSec = org.lwjgl.glfw.GLFW.glfwGetTime();
    }

    public void initHud(GuiRenderer guiRenderer, int textureAtlasId) {
        if (hudRenderer == null) {
            hudRenderer = new HudRenderer(guiRenderer, textureAtlasId);
        }
    }

    public void resize(int width, int height) {
        GL11.glViewport(0, 0, width, height);
        projectionMatrix.identity();
        projectionMatrix.perspective((float) Math.toRadians(70.0f), (float) width / height, 0.1f, 1000.0f);
    }

    public void clear() {
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
    }

    public void render(WorldRenderer worldRenderer, ClientPlayer player, int width, int height) {
        if (projectionMatrix == null || projectionMatrix.m00() == 0) {
            resize(width, height);
        }

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glCullFace(GL11.GL_BACK);

        shader.bind();
        
        float partialTick = 0.0f; // 客户端插值，暂时使用0.0f
        com.ksptool.mycraft.client.entity.Camera camera = player.getCamera();
        
        org.joml.Vector3f interpolatedPos = new org.joml.Vector3f(player.getPreviousPosition()).lerp(player.getPosition(), partialTick);
        float interpolatedYaw = camera.getPreviousYaw() + (camera.getYaw() - camera.getPreviousYaw()) * partialTick;
        float interpolatedPitch = camera.getPreviousPitch() + (camera.getPitch() - camera.getPreviousPitch()) * partialTick;
        
        camera.updateViewMatrixWithInterpolation(interpolatedPos, interpolatedYaw, interpolatedPitch, player.getEyeHeight());
        camera.setProjectionMatrix(projectionMatrix);
        org.joml.Matrix4f viewMatrix = camera.getViewMatrix();
        
        org.joml.Vector3f skyColor = worldRenderer.getSkyColor();
        GL11.glClearColor(skyColor.x, skyColor.y, skyColor.z, 1.0f);
        
        org.joml.Vector3f ambientLight = worldRenderer.getAmbientLightColor();
        shader.setAmbientLight(ambientLight);
        
        shader.setUniform("projection", projectionMatrix);
        shader.setUniform("view", viewMatrix);
        shader.setUniform("model", new org.joml.Matrix4f().identity());
        shader.setUniform("textureSampler", 0);
        shader.setUniform("u_TintColor", new org.joml.Vector3f(0.2f, 1.0f, 0.1f));
        float elapsed = (float)(org.lwjgl.glfw.GLFW.glfwGetTime() - startTimeSec);
        shader.setUniform("u_Time", elapsed);
        shader.setUniform("u_TextureSize", 16.0f);
        TextureManager tm = TextureManager.getInstance();
        shader.setUniform("u_AtlasSize", (float)tm.getAtlasWidth());
        
        if (projectionMatrix.m00() == 0) {
            log.error("ERROR: Projection matrix is not initialized!");
        }

        GL11.glDisable(GL11.GL_BLEND);
        worldRenderer.renderOpaque(shader, player.getCamera());
        
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        worldRenderer.renderTransparent(shader, player.getCamera());
        GL11.glDisable(GL11.GL_BLEND);
        
        shader.unbind();
        
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        org.lwjgl.opengl.GL30.glBindVertexArray(0);
        
        int error = GL11.glGetError();
        if (error != 0) {
            log.error("OpenGL error after rendering world: {}", error);
        }

        if (hudRenderer != null) {
            hudRenderer.render(player, width, height);
        } else {
            hotbarRenderer.render(player, width, height);
        }
        
        error = GL11.glGetError();
        if (error != 0) {
            log.error("OpenGL error after rendering HUD: {}", error);
        }
    }

    public void cleanup() {
        if (shader != null) {
            shader.cleanup();
        }
        if (hudRenderer != null) {
            hudRenderer.cleanup();
        }
        if (hotbarRenderer != null) {
            hotbarRenderer.cleanup();
        }
    }
}

