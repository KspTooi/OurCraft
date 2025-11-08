项目： "MyCraft" 性能优化 - 消除空闲 CPU 负载

背景（由任务管理器确认）： 尽管游戏在 JProfiler 中显示为“等待 V-Sync”（非阻塞），但它在“静止”时仍保持着约 10% 的高 CPU 使用率。

问题根源： 我们的 World.update() 方法每一帧（每秒 60 次）都在执行昂贵的“区块检查”循环（加载、卸载、提交），即使玩家根本没有移动到新的区块。

目标： 在 World.update() 中实现“脏检查” (Dirty Check)。仅在玩家实际跨越到新的区块坐标时，才触发昂贵的区块加载/卸载逻辑。

执行计划

修改 com.ksptool.mycraft.world.World.java：

    添加新的成员变量：

        我们需要“记住”玩家上一帧所在的区块坐标。

        private int lastPlayerChunkX = Integer.MIN_VALUE;

        private int lastPlayerChunkZ = Integer.MIN_VALUE;

    重构 World.update(Vector3f playerPosition) 方法：

        计算当前的区块坐标（不变）。

        检查是否跨越了区块边界（boolean playerMovedChunk）。

        仅当 playerMovedChunk == true 时，才执行三个昂贵的 for 循环。

        更新 lastPlayerChunkX/Z。


# 空闲 CPU 负载优化计划

此计划旨在通过在 `World.update` 方法中实现“脏检查”（Dirty Check）机制，解决游戏静止时 CPU 负载过高的问题。

## 核心修改: `World.java`

所有修改将集中在 `src/main/java/com/ksptool/mycraft/world/World.java` 文件中。

### 1. 添加新的成员变量

我将向 `World` 类添加两个私有整型变量，用于跟踪玩家上一帧所在的区块坐标。

```java
public class World {
    // ... 其他成员变量 ...
    private int lastPlayerChunkX = Integer.MIN_VALUE;
    private int lastPlayerChunkZ = Integer.MIN_VALUE;
    // ...
}
```

使用 `Integer.MIN_VALUE` 进行初始化可以保证在游戏开始时，更新逻辑会被正确地执行一次。

### 2. 重构 `update` 方法

我将重构 `update(Vector3f playerPosition)` 方法，将所有昂贵的区块管理循环包裹在一个条件判断中。

**新代码逻辑:**

```java
// 在 World.update(Vector3f playerPosition) 中

// ... (时间更新逻辑不变) ...

int playerChunkX = (int) Math.floor(playerPosition.x / Chunk.CHUNK_SIZE);
int playerChunkZ = (int) Math.floor(playerPosition.z / Chunk.CHUNK_SIZE);

// [新] 脏检查
if (playerChunkX != lastPlayerChunkX || playerChunkZ != lastPlayerChunkZ) {

    // [移动] 将所有区块管理循环移动到此代码块内
    
    // 1. 区块加载循环
    for (int x = playerChunkX - RENDER_DISTANCE; x <= playerChunkX + RENDER_DISTANCE; x++) {
        for (int z = playerChunkZ - RENDER_DISTANCE; z <= playerChunkZ + RENDER_DISTANCE; z++) {
            // ...
        }
    }

    // 2. 网格提交循环
    for (int x = playerChunkX - RENDER_DISTANCE; x <= playerChunkX + RENDER_DISTANCE; x++) {
        for (int z = playerChunkZ - RENDER_DISTANCE; z <= playerChunkZ + RENDER_DISTANCE; z++) {
            // ...
        }
    }
    
    // 3. 区块卸载循环
    chunks.entrySet().removeIf(entry -> {
        // ...
    });

    // [新] 更新最后位置
    lastPlayerChunkX = playerChunkX;
    lastPlayerChunkZ = playerChunkZ;
}
```

通过此项修改，只有当玩家移动到新的区块时，才会执行区块的加载、网格提交和卸载逻辑，从而在玩家静止时将 CPU 负载降至最低。