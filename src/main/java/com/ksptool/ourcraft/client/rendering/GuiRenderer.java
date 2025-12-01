package com.ksptool.ourcraft.client.rendering;

import org.joml.Vector2f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11; // 颜色 都在这里
import org.lwjgl.opengl.GL13; // 纹理 都在这里
import org.lwjgl.opengl.GL15; // VBO 都在这里
import org.lwjgl.opengl.GL20; // Shader 都在这里
import org.lwjgl.opengl.GL30; // VAO 都在这里
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.FloatBuffer;

/**
 * GUI渲染器类，负责渲染用户界面元素（按钮、文本框等）
 */
public class GuiRenderer {
    private static final Logger logger = LoggerFactory.getLogger(GuiRenderer.class);
    private ShaderProgram shader;
    private int vaoId;
    private int vboId;
    private TextRenderer textRenderer;
    private static final float[] quadVertices = {
        0.0f, 0.0f, 0.0f, 0.0f,
        1.0f, 0.0f, 1.0f, 0.0f,
        1.0f, 1.0f, 1.0f, 1.0f,
        0.0f, 1.0f, 0.0f, 1.0f
    };

    public GuiRenderer() {
        textRenderer = new TextRenderer();
    }

    public void init() {

        if (shader == null) {
            try {
                shader = new ShaderProgram("/shaders/ui_vertex.glsl", "/shaders/ui_fragment.glsl");
            } catch (Exception e) {
                logger.error("GuiRenderer: 着色器加载失败", e);
                return;
            }
        }

        // 1. 创建 VAO (使用 GL30，因为 VAO 是 3.0 特性)
        vaoId = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vaoId);

        // 2. 创建 VBO (统一使用 GL15，这是 VBO 诞生的版本)
        // 之前的代码混用了 GL20.glGenBuffers，虽然能跑但不规范
        vboId = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);

        // 填充数据
        FloatBuffer verticesBuffer = MemoryUtil.memAllocFloat(quadVertices.length);
        verticesBuffer.put(quadVertices).flip();

        // 3. 上传数据到显卡 (使用 GL15)
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, verticesBuffer, GL15.GL_STATIC_DRAW);
        MemoryUtil.memFree(verticesBuffer);

        // 4. 设置顶点属性指针 (使用 GL20，因为可编程管线属性是 2.0 特性)
        // 这里的 GL11.GL_FLOAT 是正确的，因为数据类型定义在 1.1
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 4 * 4, 0);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 4 * 4, 2 * 4);

        // 解绑 VBO (习惯上解绑一下，虽然不是必须的，用 GL15)
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

        // 解绑 VAO (用 GL30)
        GL30.glBindVertexArray(0);

        if (textRenderer != null) {
            textRenderer.init();
            textRenderer.setGuiRenderer(this);
        }
    }

    public void renderQuad(float x, float y, float width, float height, Vector4f color, int windowWidth, int windowHeight) {
        if (shader == null || vaoId == 0 || vboId == 0) {
            init();
            if (shader == null || vaoId == 0 || vboId == 0) {
                logger.error("GuiRenderer: 无法渲染，初始化失败");
                return;
            }
        }

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        shader.bind();
        shader.setUniform("screenSize", new Vector2f(windowWidth, windowHeight));
        shader.setUniform("position", new Vector2f(x, y));
        shader.setUniform("size", new Vector2f(width, height));
        shader.setUniform("color", color);
        shader.setUniform("u_UseTexture", false);
        shader.setUniform("u_TexCoordOffset", new Vector2f(0.0f, 0.0f));
        shader.setUniform("u_TexCoordScale", new Vector2f(1.0f, 1.0f));

        GL30.glBindVertexArray(vaoId);
        GL20.glEnableVertexAttribArray(0);
        GL20.glDisableVertexAttribArray(1);

        GL11.glDrawArrays(GL11.GL_TRIANGLE_FAN, 0, 4);

        GL20.glDisableVertexAttribArray(0);
        GL30.glBindVertexArray(0);

        shader.unbind();

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    public void renderButton(float x, float y, float width, float height, String text, boolean hovered, int windowWidth, int windowHeight) {
        if (shader == null || vaoId == 0) {
            logger.error("GuiRenderer: renderButton调用时渲染器未初始化");
            return;
        }

        Vector4f backgroundColor = new Vector4f(0.4f, 0.4f, 0.4f, 0.9f);
        if (hovered) {
            backgroundColor = new Vector4f(0.6f, 0.6f, 0.6f, 0.9f);
        }

        renderQuad(x, y, width, height, backgroundColor, windowWidth, windowHeight);

        Vector4f borderColor = new Vector4f(0.0f, 0.0f, 0.0f, 1.0f);
        float borderWidth = 2.0f;
        renderQuad(x, y, width, borderWidth, borderColor, windowWidth, windowHeight);
        renderQuad(x, y + height - borderWidth, width, borderWidth, borderColor, windowWidth, windowHeight);
        renderQuad(x, y, borderWidth, height, borderColor, windowWidth, windowHeight);
        renderQuad(x + width - borderWidth, y, borderWidth, height, borderColor, windowWidth, windowHeight);

        if (text != null && !text.isEmpty() && textRenderer != null) {
            float textScale = 1.0f;
            float textWidth = textRenderer.getTextWidth(text, textScale);
            float textHeight = 16.0f * textScale;
            float textX = x + (width - textWidth) / 2.0f;
            float textY = y + (height - textHeight) / 2.0f;
            Vector4f textColor = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);
            textRenderer.renderText(this, textX, textY, text, textScale, textColor, windowWidth, windowHeight);
        }
    }

    public void renderTexturedQuad(float x, float y, float width, float height, float u0, float v0, float u1, float v1, Vector4f color, int textureId, int windowWidth, int windowHeight) {
        if (shader == null || vaoId == 0 || vboId == 0) {
            init();
            if (shader == null || vaoId == 0 || vboId == 0) {
                logger.error("GuiRenderer: 无法渲染，初始化失败");
                return;
            }
        }

        if (textureId == 0) {
            logger.error("GuiRenderer: 无效的纹理ID");
            return;
        }

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        shader.bind();
        shader.setUniform("screenSize", new Vector2f(windowWidth, windowHeight));
        shader.setUniform("position", new Vector2f(x, y));
        shader.setUniform("size", new Vector2f(width, height));
        shader.setUniform("color", color);
        shader.setUniform("u_UseTexture", true);
        shader.setUniform("u_Texture", 0);
        shader.setUniform("u_TexCoordOffset", new Vector2f(u0, v0));
        shader.setUniform("u_TexCoordScale", new Vector2f(u1 - u0, v1 - v0));

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);

        GL30.glBindVertexArray(vaoId);
        GL20.glEnableVertexAttribArray(0);
        GL20.glEnableVertexAttribArray(1);

        GL11.glDrawArrays(GL11.GL_TRIANGLE_FAN, 0, 4);

        GL20.glDisableVertexAttribArray(0);
        GL20.glDisableVertexAttribArray(1);
        GL30.glBindVertexArray(0);

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        shader.unbind();

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }


    public TextRenderer getTextRenderer() {
        return textRenderer;
    }

    public void cleanup() {
        if (shader != null) {
            shader.cleanup();
        }
        if (vaoId != 0) {
            GL30.glDeleteVertexArrays(vaoId);
        }
        if (vboId != 0) {
            GL20.glDeleteBuffers(vboId);
        }
        if (textRenderer != null) {
            textRenderer.cleanup();
        }
    }
}

