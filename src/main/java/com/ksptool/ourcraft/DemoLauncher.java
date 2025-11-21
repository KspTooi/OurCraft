package com.ksptool.ourcraft;

import com.ksptool.ourcraft.client.rendering.ShaderProgram;
import com.ksptool.ourcraft.client.rendering.TextureManager;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ByteBuffer;

public class DemoLauncher {

    private long window;
    private ShaderProgram shaderProgram;
    private int vaoId;
    private int vboId;
    private int textureId;

    // 立方体的顶点数据 (位置, 纹理坐标, 着色, 动画数据)
    private float[] vertices;
    private int[] indices;

    // 摄像机变量
    private float cameraDistance = 3.0f; // 摄像机到原点的距离
    private float yaw = -90.0f; // 偏航角，Y轴旋转
    private float pitch = 0.0f; // 俯仰角，X轴旋转
    private float lastX = 800.0f / 2.0f; // 鼠标上次的X坐标
    private float lastY = 600.0f / 2.0f; // 鼠标上次的Y坐标
    private boolean firstMouse = true; // 第一次进入窗口时，避免视角跳动
    private float sensitivity = 0.1f; // 鼠标灵敏度
    private boolean mouseLeftPressed = false; // 鼠标左键是否被按下

    public static void main(String[] args) {
        new DemoLauncher().run();
    }

    public void run() {
        init();
        loop();
        cleanup();
    }

    private void init() {
        // 初始化GLFW
        if (!GLFW.glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        // 配置GLFW
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);


        // 创建窗口
        window = GLFW.glfwCreateWindow(800, 600, "MyCraft Simple Render", 0, 0);
        if (window == 0) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        // 设置按ESC键关闭窗口
        GLFW.glfwSetKeyCallback(window, (window, key, scancode, action, mods) -> {
            if (key == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_RELEASE) {
                GLFW.glfwSetWindowShouldClose(window, true);
            }
        });

        // 设置鼠标按键回调
        GLFW.glfwSetMouseButtonCallback(window, (window, button, action, mods) -> {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                if (action == GLFW.GLFW_PRESS) {
                    mouseLeftPressed = true;
                    // 当按下鼠标左键时，禁用鼠标光标并记录当前位置
                    GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
                    double[] xpos = new double[1];
                    double[] ypos = new double[1];
                    GLFW.glfwGetCursorPos(window, xpos, ypos);
                    lastX = (float) xpos[0];
                    lastY = (float) ypos[0];
                    firstMouse = true;
                } else if (action == GLFW.GLFW_RELEASE) {
                    mouseLeftPressed = false;
                    // 当释放鼠标左键时，恢复鼠标光标
                    GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
                }
            }
        });

        // 设置鼠标位置回调
        GLFW.glfwSetCursorPosCallback(window, (window, xpos, ypos) -> {
            // 只有在按住鼠标左键时才旋转视角
            if (!mouseLeftPressed) {
                return;
            }

            if (firstMouse) {
                lastX = (float) xpos;
                lastY = (float) ypos;
                firstMouse = false;
            }

            float xoffset = (float) xpos - lastX;
            float yoffset = lastY - (float) ypos; // 注意这里是反的，因为Y轴方向和屏幕坐标系相反
            lastX = (float) xpos;
            lastY = (float) ypos;

            xoffset *= sensitivity;
            yoffset *= sensitivity;

            yaw += xoffset;
            pitch += yoffset;

            // 限制俯仰角，避免翻转
            if (pitch > 89.0f) {
                pitch = 89.0f;
            }
            if (pitch < -89.0f) {
                pitch = -89.0f;
            }
        });

        // 获取主显示器和视频模式
        GLFWVidMode vidmode = GLFW.glfwGetVideoMode(GLFW.glfwGetPrimaryMonitor());
        // 将窗口居中
        GLFW.glfwSetWindowPos(
                window,
                (vidmode.width() - 800) / 2,
                (vidmode.height() - 600) / 2
        );

        // 使OpenGL上下文成为当前线程的上下文
        GLFW.glfwMakeContextCurrent(window);
        // 启用V-Sync
        GLFW.glfwSwapInterval(1);
        // 显示窗口
        GLFW.glfwShowWindow(window);

        // 导入OpenGL能力
        GL.createCapabilities();

        // 设置背景色
        GL11.glClearColor(0.2f, 0.3f, 0.3f, 1.0f);
        
        // 启用深度测试
        GL11.glEnable(GL11.GL_DEPTH_TEST);

        // 开启背面剔除
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glCullFace(GL11.GL_BACK);
        GL11.glFrontFace(GL11.GL_CCW);

        // 加载着色器
        shaderProgram = new ShaderProgram("/shaders/vertex.glsl", "/shaders/fragment.glsl");

        // 加载纹理
        TextureManager textureManager = TextureManager.getInstance();
        textureManager.loadAtlas();

        TextureManager.UVCoords grassTopUV = textureManager.getUVCoords("grass_top.png");
        if (grassTopUV == null) {
            throw new RuntimeException("Failed to get UV coords for grass_top.png");
        }

        TextureManager.UVCoords grassSideUV = textureManager.getUVCoords("grass_side.png");
        if (grassSideUV == null) {
            throw new RuntimeException("Failed to get UV coords for grass_side.png");
        }

        TextureManager.UVCoords dirtUV = textureManager.getUVCoords("dirt.png");
        if (dirtUV == null) {
            throw new RuntimeException("Failed to get UV coords for dirt.png");
        }

        // 更新顶点数据以使用正确的UV坐标
        // 定义一个立方体，每个面4个顶点，共6个面
        // 每个顶点包含：位置 (vec3), 纹理坐标 (vec2), 是否着色 (float), 动画数据 (vec3)
        vertices = new float[]{
                // 前面
                -0.5f, 0.5f, 0.5f,   grassSideUV.u0, grassSideUV.v0, 0f, 0.0f, 0.0f, 0.0f,
                0.5f, 0.5f, 0.5f,    grassSideUV.u1, grassSideUV.v0, 0f, 0.0f, 0.0f, 0.0f,
                -0.5f, -0.5f, 0.5f,  grassSideUV.u0, grassSideUV.v1, 0f, 0.0f, 0.0f, 0.0f,
                0.5f, -0.5f, 0.5f,   grassSideUV.u1, grassSideUV.v1, 0f, 0.0f, 0.0f, 0.0f,

                // 后面
                -0.5f, 0.5f, -0.5f,  grassSideUV.u1, grassSideUV.v0, 0f, 0.0f, 0.0f, 0.0f,
                0.5f, 0.5f, -0.5f,   grassSideUV.u0, grassSideUV.v0, 0f, 0.0f, 0.0f, 0.0f,
                -0.5f, -0.5f, -0.5f, grassSideUV.u1, grassSideUV.v1, 0f, 0.0f, 0.0f, 0.0f,
                0.5f, -0.5f, -0.5f,  grassSideUV.u0, grassSideUV.v1, 0f, 0.0f, 0.0f, 0.0f,

                // 顶部
                -0.5f, 0.5f, -0.5f,  grassTopUV.u0, grassTopUV.v0, 1.0f, 0.0f, 0.0f, 0.0f,
                0.5f, 0.5f, -0.5f,   grassTopUV.u1, grassTopUV.v0, 1.0f, 0.0f, 0.0f, 0.0f,
                -0.5f, 0.5f, 0.5f,   grassTopUV.u0, grassTopUV.v1, 1.0f, 0.0f, 0.0f, 0.0f,
                0.5f, 0.5f, 0.5f,    grassTopUV.u1, grassTopUV.v1, 1.0f, 0.0f, 0.0f, 0.0f,

                // 底部
                -0.5f, -0.5f, -0.5f, dirtUV.u0, dirtUV.v0, 0f, 0.0f, 0.0f, 0.0f,
                0.5f, -0.5f, -0.5f,  dirtUV.u1, dirtUV.v0, 0f, 0.0f, 0.0f, 0.0f,
                -0.5f, -0.5f, 0.5f,  dirtUV.u0, dirtUV.v1, 0f, 0.0f, 0.0f, 0.0f,
                0.5f, -0.5f, 0.5f,   dirtUV.u1, dirtUV.v1, 0f, 0.0f, 0.0f, 0.0f,

                // 左面
                -0.5f, 0.5f, -0.5f,  grassSideUV.u0, grassSideUV.v0, 0f, 0.0f, 0.0f, 0.0f,
                -0.5f, 0.5f, 0.5f,   grassSideUV.u1, grassSideUV.v0, 0f, 0.0f, 0.0f, 0.0f,
                -0.5f, -0.5f, -0.5f, grassSideUV.u0, grassSideUV.v1, 0f, 0.0f, 0.0f, 0.0f,
                -0.5f, -0.5f, 0.5f,  grassSideUV.u1, grassSideUV.v1, 0f, 0.0f, 0.0f, 0.0f,

                // 右面
                0.5f, 0.5f, -0.5f,   grassSideUV.u1, grassSideUV.v0, 0f, 0.0f, 0.0f, 0.0f,
                0.5f, 0.5f, 0.5f,    grassSideUV.u0, grassSideUV.v0, 0f, 0.0f, 0.0f, 0.0f,
                0.5f, -0.5f, -0.5f,  grassSideUV.u1, grassSideUV.v1, 0f, 0.0f, 0.0f, 0.0f,
                0.5f, -0.5f, 0.5f,   grassSideUV.u0, grassSideUV.v1, 0f, 0.0f, 0.0f, 0.0f,
        };


        indices = new int[]{
                // 前面 (0, 1, 2, 3) - CCW
                0, 2, 1,
                1, 2, 3,

                // 后面 (4, 5, 6, 7) - CCW
                4, 5, 6,
                5, 7, 6,

                // 顶部 (8, 9, 10, 11) - CCW
                8, 10, 9,
                9, 10, 11,

                // 底部 (12, 13, 14, 15) - CCW
                12, 13, 14,
                13, 15, 14,

                // 左面 (16, 17, 18, 19) - CCW
                16, 18, 17,
                17, 18, 19,

                // 右面 (20, 21, 22, 23) - CCW
                20, 21, 22,
                21, 23, 22,
        };

        textureId = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);

        int[] atlasPixels = textureManager.getAtlasPixels();
        int atlasWidth = textureManager.getAtlasWidth();
        int atlasHeight = textureManager.getAtlasHeight();

        ByteBuffer pixelBuffer = BufferUtils.createByteBuffer(atlasWidth * atlasHeight * 4); // RGBA 4 bytes per pixel
        for (int y = 0; y < atlasHeight; y++) {
            for (int x = 0; x < atlasWidth; x++) {
                int pixel = atlasPixels[y * atlasWidth + x];
                // ARGB to RGBA
                pixelBuffer.put((byte) ((pixel >> 16) & 0xFF)); // Red
                pixelBuffer.put((byte) ((pixel >> 8) & 0xFF));  // Green
                pixelBuffer.put((byte) (pixel & 0xFF));       // Blue
                pixelBuffer.put((byte) ((pixel >> 24) & 0xFF)); // Alpha
            }
        }
        pixelBuffer.flip();

        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, atlasWidth, atlasHeight, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixelBuffer);
        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);

        // 设置纹理参数
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST_MIPMAP_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);

        // 设置VAO和VBO
        vaoId = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vaoId);

        FloatBuffer verticesBuffer = BufferUtils.createFloatBuffer(vertices.length);
        verticesBuffer.put(vertices).flip();
        vboId = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, verticesBuffer, GL15.GL_STATIC_DRAW);

        // 位置属性
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 9 * Float.BYTES, 0);
        GL20.glEnableVertexAttribArray(0);
        // 纹理坐标属性
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 9 * Float.BYTES, 3 * Float.BYTES);
        GL20.glEnableVertexAttribArray(1);
        // 着色属性
        GL20.glVertexAttribPointer(2, 1, GL11.GL_FLOAT, false, 9 * Float.BYTES, 5 * Float.BYTES);
        GL20.glEnableVertexAttribArray(2);
        // 动画数据属性
        GL20.glVertexAttribPointer(3, 3, GL11.GL_FLOAT, false, 9 * Float.BYTES, 6 * Float.BYTES);
        GL20.glEnableVertexAttribArray(3);

        IntBuffer indicesBuffer = BufferUtils.createIntBuffer(indices.length);
        indicesBuffer.put(indices).flip();
        int eboId = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, eboId);
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indicesBuffer, GL15.GL_STATIC_DRAW);

        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);

    }

    private void loop() {
        Matrix4f projectionMatrix = new Matrix4f().perspective(
                (float) Math.toRadians(45.0f),
                800f / 600f,
                0.1f, 100.0f
        );

        Matrix4f modelMatrix = new Matrix4f().translate(0.0f, 0.0f, 0.0f);

        while (!GLFW.glfwWindowShouldClose(window)) {
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT); // 清除颜色和深度缓冲区

            // 根据角度计算摄像机在球面上的位置（围绕原点旋转）
            Vector3f cameraPos = new Vector3f();
            cameraPos.x = (float) (cameraDistance * Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)));
            cameraPos.y = (float) (cameraDistance * Math.sin(Math.toRadians(pitch)));
            cameraPos.z = (float) (cameraDistance * Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)));

            // 每一帧都重新计算视图矩阵，观察目标始终是原点
            Matrix4f viewMatrix = new Matrix4f().lookAt(
                    cameraPos, // 摄像机位置（在球面上）
                    new Vector3f(0.0f, 0.0f, 0.0f),  // 观察目标（原点，草方块的位置）
                    new Vector3f(0.0f, 1.0f, 0.0f)   // 向上向量
            );

            shaderProgram.bind();

            // 设置uniforms
            shaderProgram.setUniform("model", modelMatrix);
            shaderProgram.setUniform("view", viewMatrix);
            shaderProgram.setUniform("projection", projectionMatrix);
            shaderProgram.setUniform("textureSampler", 0);
            shaderProgram.setUniform("u_TintColor", new Vector3f(0.52f, 1.68f, 0.83f)); // 修正为蓝色
            shaderProgram.setUniform("ambientLight", new Vector3f(1.0f, 1.0f, 1.0f)); // 简单设置为全白光
            shaderProgram.setUniform("u_Time", (float) GLFW.glfwGetTime());
            shaderProgram.setUniform("u_TextureSize", (float)16);
            shaderProgram.setUniform("u_AtlasSize", (float)2048);

            // 绑定纹理
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);

            // 绘制
            GL30.glBindVertexArray(vaoId);
            GL11.glDrawElements(GL11.GL_TRIANGLES, indices.length, GL11.GL_UNSIGNED_INT, 0);
            GL30.glBindVertexArray(0);

            shaderProgram.unbind();

            GLFW.glfwSwapBuffers(window); // 交换颜色缓冲区
            GLFW.glfwPollEvents(); // 处理窗口事件
        }
    }

    private void cleanup() {
        shaderProgram.cleanup();
        GL30.glDeleteVertexArrays(vaoId);
        GL15.glDeleteBuffers(vboId);
        GL11.glDeleteTextures(textureId);
        GLFW.glfwDestroyWindow(window);
        GLFW.glfwTerminate();
        GLFW.glfwSetErrorCallback(null).free();
    }
}
