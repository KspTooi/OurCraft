package com.ksptool.mycraft.rendering;

import com.ksptool.mycraft.entity.Player;
import com.ksptool.mycraft.world.World;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

public class Renderer {
    private ShaderProgram shader;
    private Matrix4f projectionMatrix;
    private HotbarRenderer hotbarRenderer;

    public void init() {
        System.out.println("Initializing renderer...");
        shader = new ShaderProgram("/shaders/vertex.glsl", "/shaders/fragment.glsl");
        projectionMatrix = new Matrix4f();
        hotbarRenderer = new HotbarRenderer();
        System.out.println("Renderer initialized");
    }

    public void resize(int width, int height) {
        GL11.glViewport(0, 0, width, height);
        projectionMatrix.identity();
        projectionMatrix.perspective((float) Math.toRadians(70.0f), (float) width / height, 0.1f, 1000.0f);
        System.out.println("Renderer.resize: " + width + "x" + height + ", near=0.1, far=1000");
    }

    public void clear() {
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
    }

    public void render(World world, Player player, int width, int height) {
        if (projectionMatrix == null || projectionMatrix.m00() == 0) {
            resize(width, height);
        }

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glCullFace(GL11.GL_BACK);

        shader.bind();
        
        player.getCamera().update();
        player.getCamera().setProjectionMatrix(projectionMatrix);
        org.joml.Matrix4f viewMatrix = player.getCamera().getViewMatrix();
        
        shader.setUniform("projection", projectionMatrix);
        shader.setUniform("view", viewMatrix);
        shader.setUniform("model", new org.joml.Matrix4f().identity());
        shader.setUniform("textureSampler", 0);
        shader.setUniform("timeOfDay", world.getTimeOfDay());
        org.joml.Vector3f skyColor = world.getSkyColor();
        shader.setUniform("skyColor", skyColor);
        shader.setUniform("u_TintColor", new org.joml.Vector3f(0.2f, 1.0f, 0.1f));
        
        if (projectionMatrix.m00() == 0) {
            System.err.println("ERROR: Projection matrix is not initialized!");
        }

        world.render(shader, player.getCamera());
        
        int error = GL11.glGetError();
        if (error != 0) {
            System.err.println("OpenGL error after rendering: " + error);
        }

        shader.unbind();

        hotbarRenderer.render(player, width, height);
    }

    public void cleanup() {
        if (shader != null) {
            shader.cleanup();
        }
        if (hotbarRenderer != null) {
            hotbarRenderer.cleanup();
        }
    }
}

