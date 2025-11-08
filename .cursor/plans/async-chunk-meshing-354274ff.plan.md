<!-- 354274ff-2b1e-4b57-84e7-5d321c808bfe b7c113cc-ded1-4c10-b459-ce02ba279c7c -->
# 视锥剔除优化计划

该计划概述了实施视锥剔除的步骤，这将通过不渲染相机视图外的区块来提高性能。

## 第一阶段：创建视锥和包围盒逻辑

### 1. 新建文件: `Frustum.java`

我将在 `src/main/java/com/ksptool/mycraft/rendering/` 中创建一个新类 `Frustum.java`。该类将管理相机视锥的六个平面并提供相交测试。这里的关键依赖是 `org.joml.Matrix4f`，您在项目的其余部分似乎也在使用它进行矩阵数学运算。

### 2. 为 `Chunk.java` 添加包围盒

我将编辑 `src/main/java/com/ksptool/mycraft/world/Chunk.java`:

- 添加一个 `private BoundingBox boundingBox;` 字段。
- 在构造函数中，我将根据区块的世界坐标对其进行初始化：
  ```java
  float minX = chunkX * CHUNK_SIZE;
  float maxX = minX + CHUNK_SIZE;
  float minZ = chunkZ * CHUNK_SIZE;
  float maxZ = minZ + CHUNK_SIZE;
  this.boundingBox = new BoundingBox(minX, 0, minZ, maxX, CHUNK_HEIGHT, maxZ);
  ```

- 添加一个公共的 getter 方法 `getBoundingBox()`。

## 第二阶段：集成到渲染管线

### 1. 更新 `Camera.java`

我将修改 `src/main/java/com/ksptool/mycraft/entity/Camera.java` 来管理投影矩阵，使其成为相机的完整表示。

- 添加 `private Matrix4f projectionMatrix;`
- 添加 `getProjectionMatrix()` 和 `setProjectionMatrix(Matrix4f projectionMatrix)`。

### 2. 更新 `Renderer.java`

我将修改 `src/main/java/com/ksptool/mycraft/rendering/Renderer.java`:

- 在 `render` 方法中，我将把渲染器的 `projectionMatrix` 传递给相机对象：`player.getCamera().setProjectionMatrix(this.projectionMatrix);`。
- 我将把对 `world.render(shader)` 的调用更改为 `world.render(shader, player.getCamera());`。

### 3. 更新 `World.java`

这是进行剔除的地方。我将编辑 `src/main/java/com/ksptool/mycraft/world/World.java`:

- 添加 `private Frustum frustum;` 并在构造函数中初始化它。
- 将 `render` 方法的签名更改为 `public void render(ShaderProgram shader, Camera camera)`。
- 在 `render` 方法的开头，我将更新视锥：
  ```java
  frustum.update(camera.getProjectionMatrix(), camera.getViewMatrix());
  ```

- 在渲染循环中，我将添加剔除检查：
  ```java
  for (Chunk chunk : chunks.values()) {
      if (chunk != null && chunk.hasMesh()) {
          if (frustum.intersects(chunk.getBoundingBox())) {
              chunk.render();
          }
      }
  }
  ```

### To-dos

- [ ] Create the new Frustum class to handle the logic for view frustum plane extraction and intersection tests.
- [ ] Add a BoundingBox to the Chunk class, initialized in the constructor to represent the chunk's volume in world space.
- [ ] Update the Camera class to store and manage the projection matrix, making it a complete representation of the camera's properties.
- [ ] Modify the Renderer to pass the projection matrix to the camera and the camera object to the world's render method.
- [ ] Integrate the frustum culling check into the World's render method to ensure only visible chunks are drawn.