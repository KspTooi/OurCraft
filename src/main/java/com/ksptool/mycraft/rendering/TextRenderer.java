package com.ksptool.mycraft.rendering;

import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Bitmap;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FT_GlyphSlot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.lwjgl.PointerBuffer;

import static org.lwjgl.util.freetype.FreeType.*;
import static org.lwjgl.system.MemoryUtil.NULL;

import org.lwjgl.util.freetype.FreeType;

/**
 * 文字渲染器类，使用FreeType库加载字体并渲染文字
 */
public class TextRenderer {
    private static final Logger logger = LoggerFactory.getLogger(TextRenderer.class);
    private static final int FONT_SIZE = 48;
    private static final int ATLAS_WIDTH = 1024;
    private static final int ATLAS_HEIGHT = 1024;
    private static final int PADDING = 2;
    
    private long library;
    private FT_Face face;
    private ByteBuffer fontBuffer;
    private int atlasTextureId;
    private int atlasWidth;
    private int atlasHeight;
    private Map<Integer, GlyphData> glyphCache;
    
    private int currentX;
    private int currentY;
    private int currentRowHeight;
    
    public static class GlyphData {
        public float u0, v0, u1, v1;
        public int width;
        public int height;
        public int bearingX;
        public int bearingY;
        public int advance;
        
        public GlyphData(float u0, float v0, float u1, float v1, int width, int height, int bearingX, int bearingY, int advance) {
            this.u0 = u0;
            this.v0 = v0;
            this.u1 = u1;
            this.v1 = v1;
            this.width = width;
            this.height = height;
            this.bearingX = bearingX;
            this.bearingY = bearingY;
            this.advance = advance;
        }
    }
    
    public TextRenderer() {
        glyphCache = new HashMap<>();
        atlasWidth = ATLAS_WIDTH;
        atlasHeight = ATLAS_HEIGHT;
        currentX = PADDING;
        currentY = PADDING;
        currentRowHeight = 0;
    }
    
    public void init() {
        try {
            PointerBuffer libraryBuffer = MemoryUtil.memAllocPointer(1);
            int error = FT_Init_FreeType(libraryBuffer);
            if (error != 0) {
                logger.error("TextRenderer: FreeType初始化失败，错误代码: {}", error);
                MemoryUtil.memFree(libraryBuffer);
                return;
            }
            library = libraryBuffer.get(0);
            MemoryUtil.memFree(libraryBuffer);
            
            if (library == NULL) {
                logger.error("TextRenderer: FreeType库初始化失败");
                return;
            }
            
            String fontPath = "/textures/font/SmileySans-Oblique-3.otf";
            InputStream inputStream = getClass().getResourceAsStream(fontPath);
            if (inputStream == null) {
                java.io.File file = new java.io.File("src/main/resources" + fontPath);
                if (file.exists()) {
                    inputStream = new java.io.FileInputStream(file);
                } else {
                    logger.error("TextRenderer: 字体文件未找到: {}", fontPath);
                    return;
                }
            }
            
            byte[] fontData = inputStream.readAllBytes();
            inputStream.close();
            
            fontBuffer = MemoryUtil.memAlloc(fontData.length);
            fontBuffer.put(fontData);
            fontBuffer.flip();
            
            PointerBuffer faceBuffer = MemoryUtil.memAllocPointer(1);
            error = FT_New_Memory_Face(library, fontBuffer, 0L, faceBuffer);
            if (error != 0) {
                logger.error("TextRenderer: 加载字体失败，错误代码: {}", error);
                MemoryUtil.memFree(fontBuffer);
                fontBuffer = null;
                MemoryUtil.memFree(faceBuffer);
                return;
            }
            
            long faceAddress = faceBuffer.get(0);
            MemoryUtil.memFree(faceBuffer);
            
            if (faceAddress == NULL) {
                logger.error("TextRenderer: 字体面创建失败");
                MemoryUtil.memFree(fontBuffer);
                fontBuffer = null;
                return;
            }
            
            face = FT_Face.create(faceAddress);
            
            error = FT_Set_Pixel_Sizes(face, 0, FONT_SIZE);
            if (error != 0) {
                logger.error("TextRenderer: 设置字体大小失败，错误代码: {}", error);
                return;
            }
            
            createAtlasTexture();
        } catch (Exception e) {
            logger.error("TextRenderer: 初始化时出错", e);
        }
    }
    
    private void createAtlasTexture() {
        atlasTextureId = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlasTextureId);
        
        ByteBuffer emptyBuffer = MemoryUtil.memAlloc(atlasWidth * atlasHeight);
        emptyBuffer.clear();
        
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RED, atlasWidth, atlasHeight, 0, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, emptyBuffer);
        
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        
        MemoryUtil.memFree(emptyBuffer);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }
    
    private GlyphData loadGlyph(int codePoint) {
        if (glyphCache.containsKey(codePoint)) {
            return glyphCache.get(codePoint);
        }
        
        if (face == null || face.address() == NULL) {
            return null;
        }
        
        int glyphIndex = FT_Get_Char_Index(face, codePoint);
        if (glyphIndex == 0 && codePoint != ' ') {
            return null;
        }
        
        int error = FT_Load_Glyph(face, glyphIndex, FreeType.FT_LOAD_RENDER);
        if (error != 0) {
            return null;
        }
        
        FT_GlyphSlot slot = face.glyph();
        FT_Bitmap bitmap = slot.bitmap();
        
        int width = bitmap.width();
        int height = bitmap.rows();
        
        if (width == 0 || height == 0) {
            int advance = (int)(slot.advance().x() >> 6);
            GlyphData emptyGlyph = new GlyphData(0, 0, 0, 0, 0, 0, 0, 0, advance);
            glyphCache.put(codePoint, emptyGlyph);
            return emptyGlyph;
        }
        
        if (currentX + width + PADDING > atlasWidth) {
            currentX = PADDING;
            currentY += currentRowHeight + PADDING;
            currentRowHeight = 0;
        }
        
        if (currentY + height + PADDING > atlasHeight) {
            logger.warn("TextRenderer: 字体图集已满，无法添加更多字符");
            return null;
        }
        
        int x = currentX;
        int y = currentY;
        
        ByteBuffer bitmapBuffer = bitmap.buffer(width * bitmap.rows());

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlasTextureId);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, bitmap.pitch());
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, x, y, width, height, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, bitmapBuffer);
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        
        float u0 = (float) x / atlasWidth;
        float v0 = (float) y / atlasHeight;
        float u1 = (float) (x + width) / atlasWidth;
        float v1 = (float) (y + height) / atlasHeight;
        
        int bearingX = slot.bitmap_left();
        int bearingY = slot.bitmap_top();
        int advance = (int)(slot.advance().x() >> 6);
        
        GlyphData glyphData = new GlyphData(u0, v0, u1, v1, width, height, bearingX, bearingY, advance);
        glyphCache.put(codePoint, glyphData);
        
        currentX += width + PADDING;
        if (height > currentRowHeight) {
            currentRowHeight = height;
        }
        
        return glyphData;
    }
    
    public void setGuiRenderer(GuiRenderer guiRenderer) {
    }
    
    public void renderText(GuiRenderer guiRenderer, float x, float y, String text, float scale, Vector4f color, int windowWidth, int windowHeight) {
        if (guiRenderer == null) {
            return;
        }
        
        if (text == null || text.isEmpty()) {
            return;
        }
        
        if (atlasTextureId == 0) {
            return;
        }
        
        float currentX = x;
        float currentY = y;
        
        for (int i = 0; i < text.length(); i++) {
            int codePoint = text.codePointAt(i);
            if (codePoint > 0xFFFF) {
                i++;
            }
            
            if (codePoint == ' ') {
                GlyphData spaceGlyph = loadGlyph(' ');
                if (spaceGlyph != null) {
                    currentX += spaceGlyph.advance * scale;
                } else {
                    currentX += FONT_SIZE * 0.3f * scale;
                }
                continue;
            }
            
            GlyphData glyph = loadGlyph(codePoint);
            if (glyph == null || glyph.width == 0 || glyph.height == 0) {
                continue;
            }
            
            float glyphX = currentX + glyph.bearingX * scale;
            float glyphY = currentY - (glyph.height - glyph.bearingY) * scale;
            float glyphWidth = glyph.width * scale;
            float glyphHeight = glyph.height * scale;
            
            guiRenderer.renderTexturedQuad(glyphX, glyphY, glyphWidth, glyphHeight, 
                glyph.u0, glyph.v0, glyph.u1, glyph.v1, color, atlasTextureId, windowWidth, windowHeight);
            
            currentX += glyph.advance * scale;
        }
    }
    
    public float getTextWidth(String text, float scale) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        
        float width = 0;
        for (int i = 0; i < text.length(); i++) {
            int codePoint = text.codePointAt(i);
            if (codePoint > 0xFFFF) {
                i++;
            }
            
            if (codePoint == ' ') {
                GlyphData spaceGlyph = loadGlyph(' ');
                if (spaceGlyph != null) {
                    width += spaceGlyph.advance * scale;
                } else {
                    width += FONT_SIZE * 0.3f * scale;
                }
                continue;
            }
            
            GlyphData glyph = loadGlyph(codePoint);
            if (glyph != null) {
                width += glyph.advance * scale;
            }
        }
        
        return width;
    }
    
    public void cleanup() {
        if (atlasTextureId != 0) {
            GL11.glDeleteTextures(atlasTextureId);
        }
        
        if (face != null && face.address() != NULL) {
            FT_Done_Face(face);
            face = null;
        }
        
        if (fontBuffer != null) {
            MemoryUtil.memFree(fontBuffer);
            fontBuffer = null;
        }
        
        if (library != NULL) {
            FT_Done_FreeType(library);
            library = NULL;
        }
    }
}
