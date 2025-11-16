package com.ksptool.ourcraft.client.rendering;

import com.ksptool.ourcraft.client.world.ClientChunk;
import com.ksptool.ourcraft.client.world.ClientWorld;
import com.ksptool.ourcraft.client.entity.Camera;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;

import java.nio.ByteBuffer;

/**
 * 世界渲染器，负责所有与世界渲染相关的逻辑
 */
public class WorldRenderer {

    private static final int RENDER_DISTANCE = 8;
    
    private final ClientWorld clientWorld;
    private int textureId;
    private final Frustum frustum;
    
    public WorldRenderer(ClientWorld clientWorld) {
        this.clientWorld = clientWorld;
        this.frustum = new Frustum();
    }
    
    public void init() {
        loadTexture();
    }
    
    private void loadTexture() {
        TextureManager textureManager = TextureManager.getInstance();
        textureManager.loadAtlas();
        
        textureId = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);

        int[] pixels = textureManager.getAtlasPixels();
        int atlasWidth = textureManager.getAtlasWidth();
        int atlasHeight = textureManager.getAtlasHeight();

        ByteBuffer buffer = ByteBuffer.allocateDirect(atlasWidth * atlasHeight * 4);
        for (int pixel : pixels) {
            buffer.put((byte) ((pixel >> 16) & 0xFF));
            buffer.put((byte) ((pixel >> 8) & 0xFF));
            buffer.put((byte) (pixel & 0xFF));
            buffer.put((byte) ((pixel >> 24) & 0xFF));
        }
        buffer.flip();

        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, atlasWidth, atlasHeight, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
    }
    
    public void render(ShaderProgram shader, Camera camera) {
        renderOpaque(shader, camera);
        renderTransparent(shader, camera);
    }

    public void renderOpaque(ShaderProgram shader, Camera camera) {
        if (clientWorld.getChunks().isEmpty()) {
            return;
        }
        
        frustum.update(camera.getProjectionMatrix(), camera.getViewMatrix());
        
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        shader.setUniform("textureSampler", 0);

        Vector3f playerPosition = camera.getPosition();
        int playerChunkX = (int) Math.floor(playerPosition.x / ClientChunk.CHUNK_SIZE);
        int playerChunkZ = (int) Math.floor(playerPosition.z / ClientChunk.CHUNK_SIZE);

        for (int x = playerChunkX - RENDER_DISTANCE; x <= playerChunkX + RENDER_DISTANCE; x++) {
            for (int z = playerChunkZ - RENDER_DISTANCE; z <= playerChunkZ + RENDER_DISTANCE; z++) {
                
                ClientChunk clientChunk = clientWorld.getChunk(x, z);

                if (clientChunk != null && clientChunk.hasMesh()) {
                    if (frustum.intersects(clientChunk.getBoundingBox())) {
                        clientChunk.render();
                    }
                }
            }
        }
    }

    public void renderTransparent(ShaderProgram shader, Camera camera) {
        if (clientWorld.getChunks().isEmpty()) {
            return;
        }
        
        frustum.update(camera.getProjectionMatrix(), camera.getViewMatrix());
        
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        shader.setUniform("textureSampler", 0);

        Vector3f playerPosition = camera.getPosition();
        int playerChunkX = (int) Math.floor(playerPosition.x / ClientChunk.CHUNK_SIZE);
        int playerChunkZ = (int) Math.floor(playerPosition.z / ClientChunk.CHUNK_SIZE);

        for (int x = playerChunkX - RENDER_DISTANCE; x <= playerChunkX + RENDER_DISTANCE; x++) {
            for (int z = playerChunkZ - RENDER_DISTANCE; z <= playerChunkZ + RENDER_DISTANCE; z++) {
                
                ClientChunk clientChunk = clientWorld.getChunk(x, z);

                if (clientChunk != null && clientChunk.hasTransparentMesh()) {
                    if (frustum.intersects(clientChunk.getBoundingBox())) {
                        clientChunk.renderTransparent();
                    }
                }
            }
        }
    }
    
    public int getTextureId() {
        return textureId;
    }

    public Vector3f getSkyColor() {
        float t = clientWorld.getTimeOfDay();
        
        Vector3f midnight = new Vector3f(ColorPalette.SKY_MIDNIGHT);
        Vector3f sunriseStart = new Vector3f(ColorPalette.SKY_SUNRISE_START);
        Vector3f sunriseEnd = new Vector3f(ColorPalette.SKY_SUNRISE_END);
        Vector3f noon = new Vector3f(ColorPalette.SKY_NOON);
        Vector3f sunsetStart = new Vector3f(ColorPalette.SKY_SUNSET_START);
        Vector3f sunsetEnd = new Vector3f(ColorPalette.SKY_SUNSET_END);
        
        if (t < 0.125f) {
            float factor = t / 0.125f;
            factor = (float) ((1.0f - Math.cos(factor * Math.PI)) * 0.5f);
            return new Vector3f(
                midnight.x + (sunriseStart.x - midnight.x) * factor,
                midnight.y + (sunriseStart.y - midnight.y) * factor,
                midnight.z + (sunriseStart.z - midnight.z) * factor
            );
        }
        if (t < 0.25f) {
            float factor = (t - 0.125f) / 0.125f;
            factor = (float) ((1.0f - Math.cos(factor * Math.PI)) * 0.5f);
            return new Vector3f(
                sunriseStart.x + (sunriseEnd.x - sunriseStart.x) * factor,
                sunriseStart.y + (sunriseEnd.y - sunriseStart.y) * factor,
                sunriseStart.z + (sunriseEnd.z - sunriseStart.z) * factor
            );
        }
        if (t < 0.5f) {
            float factor = (t - 0.25f) / 0.25f;
            factor = (float) ((1.0f - Math.cos(factor * Math.PI)) * 0.5f);
            return new Vector3f(
                sunriseEnd.x + (noon.x - sunriseEnd.x) * factor,
                sunriseEnd.y + (noon.y - sunriseEnd.y) * factor,
                sunriseEnd.z + (noon.z - sunriseEnd.z) * factor
            );
        }
        if (t < 0.75f) {
            float factor = (t - 0.5f) / 0.25f;
            factor = (float) ((1.0f - Math.cos(factor * Math.PI)) * 0.5f);
            return new Vector3f(
                noon.x + (sunsetStart.x - noon.x) * factor,
                noon.y + (sunsetStart.y - noon.y) * factor,
                noon.z + (sunsetStart.z - noon.z) * factor
            );
        }
        if (t < 0.875f) {
            float factor = (t - 0.75f) / 0.125f;
            factor = (float) ((1.0f - Math.cos(factor * Math.PI)) * 0.5f);
            return new Vector3f(
                sunsetStart.x + (sunsetEnd.x - sunsetStart.x) * factor,
                sunsetStart.y + (sunsetEnd.y - sunsetStart.y) * factor,
                sunsetStart.z + (sunsetEnd.z - sunsetStart.z) * factor
            );
        }
        float factor = (t - 0.875f) / 0.125f;
        factor = (float) ((1.0f - Math.cos(factor * Math.PI)) * 0.5f);
        return new Vector3f(
            sunsetEnd.x + (midnight.x - sunsetEnd.x) * factor,
            sunsetEnd.y + (midnight.y - sunsetEnd.y) * factor,
            sunsetEnd.z + (midnight.z - sunsetEnd.z) * factor
        );
    }

    public Vector3f getAmbientLightColor() {
        float t = clientWorld.getTimeOfDay();
        Vector3f midnight = new Vector3f(ColorPalette.AMBIENT_MIDNIGHT);
        Vector3f noon = new Vector3f(ColorPalette.AMBIENT_NOON);
        
        float factor;
        if (t < 0.5f) {
            factor = t / 0.5f;
            factor = (float) ((1.0f - Math.cos(factor * Math.PI)) * 0.5f);
            return new Vector3f(
                midnight.x + (noon.x - midnight.x) * factor,
                midnight.y + (noon.y - midnight.y) * factor,
                midnight.z + (noon.z - midnight.z) * factor
            );
        }
        factor = (1.0f - t) / 0.5f;
        factor = (float) ((1.0f - Math.cos(factor * Math.PI)) * 0.5f);
        
        return new Vector3f(
            midnight.x + (noon.x - midnight.x) * factor,
            midnight.y + (noon.y - midnight.y) * factor,
            midnight.z + (noon.z - midnight.z) * factor
        );
    }
    
    public void cleanup() {
        GL11.glDeleteTextures(textureId);
    }
}
