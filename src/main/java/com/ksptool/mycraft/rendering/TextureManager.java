package com.ksptool.mycraft.rendering;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

/**
 * 纹理图集管理类，负责加载和管理方块纹理图集
 */
@Slf4j
public class TextureManager {

    //纹理大小
    private static final int TEXTURE_SIZE = 16;
    
    //纹理图集大小
    private static final int ATLAS_SIZE = 2048;

    //纹理图集管理器实例
    private static TextureManager instance;

    //纹理UV坐标映射表
    private final Map<String, UVCoords> textureUVMap;

    //纹理像素
    private int[] atlasPixels;

    //纹理图集宽度
    private int atlasWidth;

    //纹理图集高度
    private int atlasHeight;

    public static class UVCoords {
        public float u0, v0, u1, v1;
        public boolean isAnimated;
        public int frameCount;
        public float frameTime;

        public UVCoords(float u0, float v0, float u1, float v1) {
            this.u0 = u0;
            this.v0 = v0;
            this.u1 = u1;
            this.v1 = v1;
            this.isAnimated = false;
            this.frameCount = 1;
            this.frameTime = 1.0f;
        }

        public UVCoords(float u0, float v0, float u1, float v1, boolean isAnimated, int frameCount, float frameTime) {
            this.u0 = u0;
            this.v0 = v0;
            this.u1 = u1;
            this.v1 = v1;
            this.isAnimated = isAnimated;
            this.frameCount = frameCount;
            this.frameTime = frameTime;
        }
    }

    private TextureManager() {
        textureUVMap = new HashMap<>();
    }

    public static TextureManager getInstance() {
        if (instance == null) {
            instance = new TextureManager();
        }
        return instance;
    }

    public void loadAtlas() {
        String texturePath = "/textures/blocks/";
        java.util.List<String> textureFiles = new java.util.ArrayList<>();

        try {
            java.io.File dir = new java.io.File("src/main/resources" + texturePath);
            if (dir.exists() && dir.isDirectory()) {
                java.io.File[] files = dir.listFiles((d, name) -> name.endsWith(".png"));
                if (files != null) {
                    for (java.io.File file : files) {
                        textureFiles.add(file.getName());
                    }
                }
            }

            if (textureFiles.isEmpty()) {
                java.net.URL resourceUrl = getClass().getResource(texturePath);
                if (resourceUrl != null && "file".equals(resourceUrl.getProtocol())) {
                    java.io.File resourceDir = new java.io.File(resourceUrl.getPath());
                    if (resourceDir.exists() && resourceDir.isDirectory()) {
                        java.io.File[] resourceFiles = resourceDir.listFiles((d, name) -> name.endsWith(".png"));
                        if (resourceFiles != null) {
                            for (java.io.File file : resourceFiles) {
                                textureFiles.add(file.getName());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error listing texture directory: {}", e.getMessage(), e);
        }

        if (textureFiles.isEmpty()) {
            textureFiles.add("dirt.png");
            textureFiles.add("grass_top.png");
            textureFiles.add("grass_side.png");
            textureFiles.add("stone.png");
            textureFiles.add("log_oak.png");
            textureFiles.add("leaves_oak.png");
        }

        java.util.Collections.sort(textureFiles);
        
        log.info("Found " + textureFiles.size() + " texture files to load");
        for (String name : textureFiles) {
            log.info("  - " + name);
        }

        atlasPixels = new int[ATLAS_SIZE * ATLAS_SIZE];
        atlasWidth = ATLAS_SIZE;
        atlasHeight = ATLAS_SIZE;

        int currentX = 0;
        int currentY = 0;
        int rowHeight = 0;

        for (String textureName : textureFiles) {
            log.info("Attempting to load texture: " + textureName);
            TextureLoadResult loadResult = loadTexture(texturePath + textureName);
            if (loadResult == null || loadResult.pixels == null) {
                log.warn("Failed to load texture: " + textureName);
                continue;
            }

            int texWidth = loadResult.imgWidth;
            int texHeight = loadResult.imgHeight;

            if (currentX + texWidth > ATLAS_SIZE) {
                currentY += rowHeight;
                currentX = 0;
                rowHeight = 0;

                if (currentY + texHeight > ATLAS_SIZE) {
                    log.error("Atlas too small! Cannot fit texture: {}", textureName);
                    break;
                }
            }

            int atlasX = currentX;
            int atlasY = currentY;

            for (int y = 0; y < texHeight; y++) {
                for (int x = 0; x < texWidth; x++) {
                    int srcIndex = y * texWidth + x;
                    int dstIndex = (atlasY + y) * ATLAS_SIZE + (atlasX + x);
                    if (dstIndex < atlasPixels.length && srcIndex < loadResult.pixels.length) {
                        atlasPixels[dstIndex] = loadResult.pixels[srcIndex];
                    }
                }
            }

            float epsilon = 0.5f / ATLAS_SIZE;
            float u0 = (float) atlasX / ATLAS_SIZE + epsilon;
            float v0 = (float) atlasY / ATLAS_SIZE + epsilon;
            float u1 = (float) (atlasX + texWidth) / ATLAS_SIZE - epsilon;

            int heightForUV = loadResult.isAnimated ? TEXTURE_SIZE : texHeight;
            float v1 = (float) (atlasY + heightForUV) / ATLAS_SIZE - epsilon;

            log.info("Loaded texture: " + textureName);
            textureUVMap.put(textureName, new UVCoords(u0, v0, u1, v1, loadResult.isAnimated, loadResult.frameCount, loadResult.frameTime));

            currentX += texWidth;
            rowHeight = Math.max(rowHeight, texHeight);
        }
    }

    private static class TextureLoadResult {
        int[] pixels;
        boolean isAnimated;
        int frameCount;
        float frameTime;
        int imgWidth;
        int imgHeight;

        TextureLoadResult(int[] pixels, boolean isAnimated, int frameCount, float frameTime, int imgWidth, int imgHeight) {
            this.pixels = pixels;
            this.isAnimated = isAnimated;
            this.frameCount = frameCount;
            this.frameTime = frameTime;
            this.imgWidth = imgWidth;
            this.imgHeight = imgHeight;
        }
    }

    private static class AnimationMetadata {
        AnimationData animation;

        static class AnimationData {
            Integer frametime;
            Boolean interpolate;
            Integer[] frames;
        }
    }

    private TextureLoadResult loadTexture(String path) {
        try {
            log.debug("Loading texture from path: " + path);
            InputStream inputStream = getClass().getResourceAsStream(path);
            if (inputStream == null) {
                java.io.File file = new java.io.File("src/main/resources" + path);
                log.debug("Resource stream is null, trying file: " + file.getAbsolutePath() + " (exists: " + file.exists() + ")");
                if (file.exists()) {
                    inputStream = new java.io.FileInputStream(file);
                } else {
                    log.error("Texture not found: " + path + " (tried: " + file.getAbsolutePath() + ")");
                    return null;
                }
            }

            byte[] bytes = inputStream.readAllBytes();
            inputStream.close();

            ByteBuffer imageBuffer = BufferUtils.createByteBuffer(bytes.length);
            imageBuffer.put(bytes);
            imageBuffer.flip();

            IntBuffer width = BufferUtils.createIntBuffer(1);
            IntBuffer height = BufferUtils.createIntBuffer(1);
            IntBuffer channels = BufferUtils.createIntBuffer(1);

            ByteBuffer image = STBImage.stbi_load_from_memory(imageBuffer, width, height, channels, 4);
            if (image == null) {
                log.error("Failed to load texture: " + path + " - " + STBImage.stbi_failure_reason());
                return null;
            }

            int imgWidth = width.get(0);
            int imgHeight = height.get(0);
            log.debug("Loaded texture from " + path + ": " + imgWidth + "x" + imgHeight);

            boolean isAnimatedTexture = false;
            int frameCount = 1;
            float frameTime = 1.0f / 20.0f;

            String mcmetaPath = path + ".mcmeta";
            AnimationMetadata animMeta = loadAnimationMetadata(mcmetaPath);
            
            if (animMeta != null && animMeta.animation != null) {
                isAnimatedTexture = true;
                if (animMeta.animation.frames != null && animMeta.animation.frames.length > 0) {
                    frameCount = animMeta.animation.frames.length;
                } else {
                    frameCount = imgHeight > TEXTURE_SIZE && imgWidth == TEXTURE_SIZE && imgHeight % TEXTURE_SIZE == 0 
                        ? (imgHeight / TEXTURE_SIZE) : 1;
                }
                if (animMeta.animation.frametime != null) {
                    frameTime = animMeta.animation.frametime / 20.0f;
                } else {
                    frameTime = 1.0f / 20.0f;
                }
                log.debug("Found animation metadata for " + path + ": frames=" + frameCount + ", frametime=" + frameTime);
            }

            int totalPixels = imgWidth * imgHeight;
            int[] pixels = new int[totalPixels];
            int imageSize = imgWidth * imgHeight * 4;

            for (int y = 0; y < imgHeight; y++) {
                for (int x = 0; x < imgWidth; x++) {
                    int srcIndex = (y * imgWidth + x) * 4;
                    int dstIndex = y * imgWidth + x;

                    if (srcIndex + 3 < imageSize && srcIndex + 3 < image.capacity()) {
                        int r = image.get(srcIndex) & 0xFF;
                        int g = image.get(srcIndex + 1) & 0xFF;
                        int b = image.get(srcIndex + 2) & 0xFF;
                        int a = image.get(srcIndex + 3) & 0xFF;

                        pixels[dstIndex] = (a << 24) | (r << 16) | (g << 8) | b;
                    }
                }
            }

            STBImage.stbi_image_free(image);
            return new TextureLoadResult(pixels, isAnimatedTexture, frameCount, frameTime, imgWidth, imgHeight);
        } catch (IOException e) {
            log.error("Error loading texture: {} - {}", path, e.getMessage());
            return null;
        }
    }

    private AnimationMetadata loadAnimationMetadata(String mcmetaPath) {
        try {
            InputStream inputStream = getClass().getResourceAsStream(mcmetaPath);
            if (inputStream == null) {
                java.io.File file = new java.io.File("src/main/resources" + mcmetaPath);
                if (file.exists()) {
                    inputStream = new java.io.FileInputStream(file);
                } else {
                    return null;
                }
            }

            String jsonContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            inputStream.close();

            JsonObject jsonObject = JsonParser.parseString(jsonContent).getAsJsonObject();
            Gson gson = new Gson();
            return gson.fromJson(jsonObject, AnimationMetadata.class);
        } catch (Exception e) {
            log.debug("Could not load animation metadata from {}: {}", mcmetaPath, e.getMessage());
            return null;
        }
    }

    public UVCoords getUVCoords(String textureName) {
        return textureUVMap.get(textureName);
    }

    public int[] getAtlasPixels() {
        return atlasPixels;
    }

    public int getAtlasWidth() {
        return atlasWidth;
    }

    public int getAtlasHeight() {
        return atlasHeight;
    }
}

