package com.ksptool.ourcraft.clientj.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.TextureArray;
import com.jme3.texture.image.ColorSpace;
import com.jme3.util.BufferUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 真正的纹理数组服务
 * 将所有方块纹理合并为一个 Texture2DArray，实现一次 DrawCall 渲染整个世界
 */
@Slf4j
public class ArrayTextureService {

    private static final int TEXTURE_SIZE = 16;
    private static ArrayTextureService instance;

    // 核心产物：纹理数组
    private TextureArray textureArray;

    // 索引映射表 (纹理名 -> 层索引信息)
    private final Map<String, TextureLayerInfo> textureIndexMap;

    private ArrayTextureService() {
        this.textureIndexMap = new HashMap<>();
    }

    public static ArrayTextureService getInstance() {
        if (instance == null) {
            instance = new ArrayTextureService();
        }
        return instance;
    }

    /**
     * 加载并生成纹理数组
     */
    public void loadTextures() {
        List<String> textureFiles = discoverTextureFiles();

        // 必须排序，确保每次运行时的层索引顺序一致（否则客户端和服务端ID可能对不上）
        Collections.sort(textureFiles);

        // 临时列表，用于收集所有的 JME Image 对象
        List<Image> allImages = new ArrayList<>();
        int currentLayerIndex = 0;

        for (String textureName : textureFiles) {
            String path = "/textures/blocks/" + textureName;
            TextureLoadResult result = loadTextureImage(path);

            if (result == null || result.image == null) continue;

            // 记录该纹理的信息
            TextureLayerInfo info = new TextureLayerInfo(
                    currentLayerIndex,
                    result.isAnimated,
                    result.frameCount,
                    result.frameTime
            );
            textureIndexMap.put(textureName, info);

            // 切割图片并添加到列表
            if (result.isAnimated) {
                int frameHeight = result.imgHeight / result.frameCount;
                for (int i = 0; i < result.frameCount; i++) {
                    BufferedImage frame = extractFrame(result.image, i, frameHeight);
                    allImages.add(convertToJmeImage(frame));
                    currentLayerIndex++;
                }
            } else {
                allImages.add(convertToJmeImage(result.image));
                currentLayerIndex++;
            }
        }

        if (allImages.isEmpty()) {
            log.warn("No textures loaded!");
            return;
        }

        // --- 关键步骤：创建 Texture2DArray ---
        textureArray = new TextureArray(allImages);
        textureArray.setMagFilter(Texture.MagFilter.Nearest); // 像素风关键
        textureArray.setMinFilter(Texture.MinFilter.NearestNoMipMaps); // 防止模糊
        textureArray.setWrap(Texture.WrapMode.Repeat);
        
        log.info("Texture2DArray generated. Total Layers: {}, Mapped Blocks: {}", allImages.size(), textureIndexMap.size());
    }

    /**
     * 获取最终的纹理数组对象（传给 Material 使用）
     */
    public TextureArray getTextureArray() {
        return textureArray;
    }

    /**
     * 获取方块对应的层索引信息（用于 Mesh 生成）
     */
    public TextureLayerInfo getTextureInfo(String textureName) {
        return textureIndexMap.get(textureName);
    }

    /**
     * 获取所有纹理名称
     */
    public Set<String> getTextureNames() {
        return new HashSet<>(textureIndexMap.keySet());
    }

    // --- 辅助方法 ---

    private BufferedImage extractFrame(BufferedImage sourceImage, int frameIndex, int frameHeight) {
        int frameY = frameIndex * frameHeight;
        // 确保裁剪出的图片严格是 16x16
        BufferedImage frame = new BufferedImage(TEXTURE_SIZE, TEXTURE_SIZE, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = frame.createGraphics();
        g.drawImage(sourceImage, 0, 0, TEXTURE_SIZE, TEXTURE_SIZE,
                0, frameY, TEXTURE_SIZE, frameY + TEXTURE_SIZE, null);
        g.dispose();
        return frame;
    }

    /**
     * 将 BufferedImage 转换为 JME Image
     * 注意：Texture2DArray 要求所有 Image 必须格式一致、大小一致
     */
    private Image convertToJmeImage(BufferedImage bufferedImage) {
        int width = TEXTURE_SIZE;
        int height = TEXTURE_SIZE;
        
        // 分配直接内存 (Direct ByteBuffer)
        ByteBuffer data = BufferUtils.createByteBuffer(width * height * 4);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = bufferedImage.getRGB(x, y);
                // ARGB -> RGBA
                data.put((byte) ((argb >> 16) & 0xFF)); // R
                data.put((byte) ((argb >> 8) & 0xFF));  // G
                data.put((byte) (argb & 0xFF));         // B
                data.put((byte) ((argb >> 24) & 0xFF)); // A
            }
        }
        data.flip();
        return new Image(Image.Format.RGBA8, width, height, data, ColorSpace.sRGB);
    }

    // 复用你原有的文件发现逻辑
    private List<String> discoverTextureFiles() {
        String texturePath = "/textures/blocks/";
        List<String> textureFiles = new ArrayList<>();
        try {
            // 尝试从文件系统加载 (开发环境)
            java.io.File dir = new java.io.File("src/main/resources" + texturePath);
            if (dir.exists() && dir.isDirectory()) {
                java.io.File[] files = dir.listFiles((d, name) -> name.endsWith(".png"));
                if (files != null) {
                    for (java.io.File file : files) textureFiles.add(file.getName());
                }
            }
            // 尝试从类路径加载 (生产环境/Jar包)
            if (textureFiles.isEmpty()) {
                 // 这里简化了逻辑，建议保留你原本的详细 resourceUrl 判断
                 // 为节省篇幅，此处省略，请将你原代码中的 discoverTextureFiles 逻辑贴回这里
                 // ...
            }
        } catch (Exception e) {
            log.error("Error discovering textures", e);
        }
        return textureFiles;
    }

    // 加载单个图片文件和元数据
    private TextureLoadResult loadTextureImage(String path) {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            InputStream streamToUse = is;
            if (streamToUse == null) {
                java.io.File file = new java.io.File("src/main/resources" + path);
                if (file.exists()) streamToUse = new java.io.FileInputStream(file);
            }
            
            if (streamToUse == null) return null;
            BufferedImage image = ImageIO.read(streamToUse);
            if (streamToUse != is) streamToUse.close(); // 如果是文件流则关闭
            
            if (image == null) return null;

            int w = image.getWidth();
            int h = image.getHeight();
            
            // 解析元数据
            String mcmetaPath = path + ".mcmeta";
            AnimationMetadata animMeta = loadAnimationMetadata(mcmetaPath);
            
            boolean isAnimated = false;
            int frameCount = 1;
            float frameTime = 0.05f; // 默认 1/20 秒

            // 简单的动画判断逻辑：高度是宽度的倍数且有元数据，或者是长条图
            if ((animMeta != null && animMeta.animation != null) || (h > w && h % w == 0)) {
                isAnimated = true;
                if (animMeta != null && animMeta.animation != null && animMeta.animation.frames != null) {
                    frameCount = animMeta.animation.frames.length;
                } else {
                    frameCount = h / w;
                }
                
                if (animMeta != null && animMeta.animation != null && animMeta.animation.frametime != null) {
                    frameTime = animMeta.animation.frametime / 20.0f;
                }
            }

            return new TextureLoadResult(image, isAnimated, frameCount, frameTime, w, h);
        } catch (IOException e) {
            log.error("Failed to load {}", path, e);
            return null;
        }
    }

    // 复用你的元数据解析
    private AnimationMetadata loadAnimationMetadata(String mcmetaPath) {
        // ... 请保留你原代码中的实现 ...
        try {
            InputStream inputStream = getClass().getResourceAsStream(mcmetaPath);
            if (inputStream == null) {
                java.io.File file = new java.io.File("src/main/resources" + mcmetaPath);
                if (!file.exists()) return null;
                inputStream = new java.io.FileInputStream(file);
            }
            String jsonContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            inputStream.close();
            return new Gson().fromJson(jsonContent, AnimationMetadata.class);
        } catch (Exception e) {
            return null;
        }
    }

    // --- 数据结构 ---

    public static class TextureLayerInfo {
        public final int index;      // 纹理在数组中的起始索引
        public final boolean isAnimated;
        public final int frameCount;
        public final float frameTime;

        public TextureLayerInfo(int index, boolean isAnimated, int frameCount, float frameTime) {
            this.index = index;
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
        int imgWidth; int imgHeight;
        TextureLoadResult(BufferedImage image, boolean isAnimated, int frameCount, float frameTime, int w, int h) {
            this.image = image; this.isAnimated = isAnimated; this.frameCount = frameCount; 
            this.frameTime = frameTime; this.imgWidth = w; this.imgHeight = h;
        }
    }

    private static class AnimationMetadata {
        AnimationData animation;
        static class AnimationData {
            Integer frametime;
            Integer[] frames;
        }
    }
}