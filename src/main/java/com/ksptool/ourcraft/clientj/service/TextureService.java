package com.ksptool.ourcraft.clientj.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * JME纹理图集管理类，负责加载和管理方块纹理图集
 */
@Slf4j
public class TextureService {

    // 纹理大小
    private static final int TEXTURE_SIZE = 16;

    // 纹理图集大小
    private static final int ATLAS_SIZE = 2048;

    // 纹理图集管理器实例
    private static TextureService instance;

    // 纹理UV坐标映射表
    private final Map<String, UVCoords> textureUVMap;

    // 纹理图集
    private Texture atlasTexture;

    // 纹理图集宽度
    private int atlasWidth;

    // 纹理图集高度
    private int atlasHeight;

    private TextureService() {
        textureUVMap = new HashMap<>();
    }

    public static TextureService getInstance() {
        if (instance == null) {
            instance = new TextureService();
        }
        return instance;
    }

    public void loadAtlas() {
        String texturePath = "/textures/blocks/";
        List<String> textureFiles = new ArrayList<>();

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

        Collections.sort(textureFiles);

        BufferedImage atlasImage = new BufferedImage(ATLAS_SIZE, ATLAS_SIZE, BufferedImage.TYPE_INT_ARGB);
        atlasWidth = ATLAS_SIZE;
        atlasHeight = ATLAS_SIZE;

        int currentX = 0;
        int currentY = 0;
        int rowHeight = 0;

        for (String textureName : textureFiles) {
            TextureLoadResult loadResult = loadTexture(texturePath + textureName);
            if (loadResult == null || loadResult.image == null) {
                log.warn("Failed to load texture: {}", textureName);
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

            // 将纹理绘制到图集上
            java.awt.Graphics2D g = atlasImage.createGraphics();
            g.drawImage(loadResult.image, atlasX, atlasY, null);
            g.dispose();

            float epsilon = 0.5f / ATLAS_SIZE;
            float u0 = (float) atlasX / ATLAS_SIZE + epsilon;
            float v0 = (float) atlasY / ATLAS_SIZE + epsilon;
            float u1 = (float) (atlasX + texWidth) / ATLAS_SIZE - epsilon;

            int heightForUV = loadResult.isAnimated ? TEXTURE_SIZE : texHeight;
            float v1 = (float) (atlasY + heightForUV) / ATLAS_SIZE - epsilon;

            textureUVMap.put(textureName,
                    new UVCoords(u0, v0, u1, v1, loadResult.isAnimated, loadResult.frameCount, loadResult.frameTime));

            currentX += texWidth;
            rowHeight = Math.max(rowHeight, texHeight);
        }

        // 将 BufferedImage 转换为 JME Texture
        atlasTexture = convertToJmeTexture(atlasImage);
        log.info("Texture atlas loaded: {} textures, size: {}x{}", textureUVMap.size(), atlasWidth, atlasHeight);
    }

    private TextureLoadResult loadTexture(String path) {
        try {
            log.debug("Loading texture from path: {}", path);
            InputStream inputStream = getClass().getResourceAsStream(path);
            if (inputStream == null) {
                java.io.File file = new java.io.File("src/main/resources" + path);
                log.debug("Resource stream is null, trying file: {} (exists: {})", file.getAbsolutePath(), file.exists());
                if (file.exists()) {
                    inputStream = new java.io.FileInputStream(file);
                } else {
                    log.error("Texture not found: {} (tried: {})", path, file.getAbsolutePath());
                    return null;
                }
            }

            BufferedImage image = ImageIO.read(inputStream);
            inputStream.close();

            if (image == null) {
                log.error("Failed to load texture: {}", path);
                return null;
            }

            int imgWidth = image.getWidth();
            int imgHeight = image.getHeight();
            log.debug("Loaded texture from {}: {}x{}", path, imgWidth, imgHeight);

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
                            ? (imgHeight / TEXTURE_SIZE)
                            : 1;
                }
                if (animMeta.animation.frametime != null) {
                    frameTime = animMeta.animation.frametime / 20.0f;
                } else {
                    frameTime = 1.0f / 20.0f;
                }
                log.debug("Found animation metadata for {}: frames={}, frametime={}", path, frameCount, frameTime);
            }

            return new TextureLoadResult(image, isAnimatedTexture, frameCount, frameTime, imgWidth, imgHeight);
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

    private Texture convertToJmeTexture(BufferedImage bufferedImage) {
        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();
        byte[] data = new byte[width * height * 4];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = bufferedImage.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;

                int index = (y * width + x) * 4;
                data[index] = (byte) r;
                data[index + 1] = (byte) g;
                data[index + 2] = (byte) b;
                data[index + 3] = (byte) a;
            }
        }

        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocateDirect(data.length);
        buffer.put(data);
        buffer.flip();

        Image jmeImage = new Image(Image.Format.RGBA8, width, height, buffer, ColorSpace.sRGB);
        Texture2D texture = new Texture2D(jmeImage);
        texture.setMagFilter(Texture.MagFilter.Nearest);
        texture.setMinFilter(Texture.MinFilter.NearestNoMipMaps);
        return texture;
    }

    public UVCoords getUVCoords(String textureName) {
        return textureUVMap.get(textureName);
    }

    public Texture getTexture() {
        return atlasTexture;
    }

    public int getAtlasWidth() {
        return atlasWidth;
    }

    public int getAtlasHeight() {
        return atlasHeight;
    }

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

    private static class TextureLoadResult {
        BufferedImage image;
        boolean isAnimated;
        int frameCount;
        float frameTime;
        int imgWidth;
        int imgHeight;

        TextureLoadResult(BufferedImage image, boolean isAnimated, int frameCount, float frameTime, int imgWidth,
                          int imgHeight) {
            this.image = image;
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
}

