package com.ksptool.ourcraft.debug;

import com.jme3.app.SimpleApplication;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.MouseAxisTrigger;
import com.jme3.font.BitmapText;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import com.jme3.system.AppSettings;
import com.ksptool.ourcraft.client.network.ClientNetworkService;
import com.ksptool.ourcraft.client.network.ClientNetworkSession;
import com.ksptool.ourcraft.sharedcore.GlobalPalette;
import com.ksptool.ourcraft.sharedcore.Registry;
import com.ksptool.ourcraft.sharedcore.enums.BlockEnums;
import com.ksptool.ourcraft.sharedcore.network.ndto.PsAllowNDto;
import com.ksptool.ourcraft.sharedcore.network.ndto.PsFinishNDto;
import com.ksptool.ourcraft.sharedcore.network.nvo.*;
import com.ksptool.ourcraft.sharedcore.network.packets.PlayerInputStateNDto;
import com.ksptool.ourcraft.sharedcore.utils.FlexChunkData;
import com.ksptool.ourcraft.sharedcore.utils.FlexChunkSerializer;
import com.ksptool.ourcraft.sharedcore.utils.position.ChunkPos;
import com.ksptool.ourcraft.sharedcore.world.BlockState;
import com.ksptool.ourcraft.sharedcore.BoundingBox;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3d;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;

@Slf4j
public class DebugClient extends SimpleApplication {

    private ClientNetworkService networkService;
    private ClientNetworkSession session;
    
    // 使用JmeFlexClientChunk管理区块
    private final Map<ChunkPos, DebugFlexClientChunk> chunkMap = new HashMap<>();
    
    // 脏区块队列
    private final Queue<ChunkPos> dirtyChunkQueue = new ArrayDeque<>();
    
    // 网格生成器
    private DebugFlexChunkMeshGenerator meshGenerator;
    
    // 待处理的网格生成任务
    private final List<Future<DebugMeshGenerationResult>> pendingMeshFutures = new ArrayList<>();
    
    // 物理参数（使用 Vector3d 匹配服务端精度）
    private Vector3d velocity = new Vector3d(0, 0, 0);
    private BoundingBox boundingBox;
    private boolean onGround = false;
    
    // 固定时间步长相关
    private double tickAccumulator = 0.0;
    private double tickInterval = 0.05; // 默认 20 TPS，将由服务端同步覆盖
    
    // 从服务端同步的参数
    private float groundAcceleration = 40.0f; 
    private float airAcceleration = 5.0f;
    private float maxSpeed = 40.0f;
    private int aps = 20; 
    
    // 硬编码的物理常数 (与服务端 ServerLivingEntity 一致)
    private static final float GRAVITY = -20.0f;
    private static final float JUMP_VELOCITY = 8.0f;
    private static final float GROUND_FRICTION = 0.6f;
    private static final float AIR_FRICTION = 0.91f;

    private double playerX = 0;
    private double playerY = 64;
    private double playerZ = 0;
    
    private boolean wPressed = false;
    private boolean sPressed = false;
    private boolean aPressed = false;
    private boolean dPressed = false;
    private boolean spacePressed = false;
    
    // 记录上一帧是否有输入活动，用于在静止时不发送数据包
    private boolean lastInputWasActive = false;
    
    private int clientTick = 0;
    private boolean inWorld = false;
    
    private Node rootNode3D;
    private Node uiNode;
    
    private static final int CHUNK_SIZE_X = 16;
    private static final int CHUNK_SIZE_Z = 16;
    
    private float cameraZoom = 50.0f;
    private BitmapText coordsText;
    
    // 高度标签相关
    private final List<BitmapText> heightLabelPool = new ArrayList<>();
    private final List<BitmapText> activeHeightLabels = new ArrayList<>();
    private Node labelsNode;
    private DebugClientWorld clientWorld;

    // 每帧最多处理的脏区块数量
    private static final int MAX_DIRTY_CHUNKS_PER_FRAME = 2;
    
    @Override
    public void simpleInitApp() {
        log.info("初始化调试客户端...");
        
        viewPort.setBackgroundColor(new ColorRGBA(0.5f, 0.7f, 1.0f, 1.0f));
        
        // 初始化坐标显示文本
        coordsText = new BitmapText(guiFont);
        coordsText.setSize(guiFont.getCharSet().getRenderedSize());
        coordsText.setColor(ColorRGBA.White);
        coordsText.setText("XYZ: 0, 0, 0");
        coordsText.setLocalTranslation(10, settings.getHeight() - 10, 0);
        guiNode.attachChild(coordsText);
        
        registerBlocks();
        GlobalPalette.getInstance().bake();
        log.info("方块注册完成，调色板已烘焙");
        
        // 创建简化的World实现用于网格生成器
        clientWorld = new DebugClientWorld();
        // DebugSharedWorld debugWorld = new DebugSharedWorld();
        meshGenerator = new DebugFlexChunkMeshGenerator(clientWorld::getBlockState);
        
        setupCamera();
        setupInput();
        
        rootNode3D = new Node("Root3D");
        uiNode = new Node("UI");
        labelsNode = new Node("Labels");
        
        rootNode.attachChild(rootNode3D);
        rootNode.attachChild(uiNode);
        rootNode3D.attachChild(labelsNode);
        
        connectToServer();
    }
    
    private void registerBlocks() {
        Registry registry = Registry.getInstance();
        BlockEnums.registerBlocks(registry);
    }
    
    private void setupCamera() {
        Camera cam = getCamera();
        cam.setLocation(new Vector3f(0, 200, 0));
        cam.lookAt(Vector3f.ZERO, Vector3f.UNIT_Y);
        cam.setParallelProjection(true);
        float aspect = (float) cam.getWidth() / cam.getHeight();
        float frustumSize = 100;
        cam.setFrustum(-1000, 1000, -frustumSize * aspect, frustumSize * aspect, frustumSize, -frustumSize);
    }
    
    private void updateCamera() {
        Camera cam = getCamera();
        cam.setLocation(new Vector3f((float)playerX, (float)playerY + 200, (float)playerZ));
        cam.lookAt(new Vector3f((float)playerX, (float)playerY, (float)playerZ), Vector3f.UNIT_Y);
        
        float aspect = (float) cam.getWidth() / cam.getHeight();
        float frustumSize = cameraZoom;
        cam.setFrustum(-1000, 1000, -frustumSize * aspect, frustumSize * aspect, frustumSize, -frustumSize);
    }
    
    private void updateCoordsText() {
        if (coordsText != null) {
            coordsText.setText(String.format("XYZ: %.2f, %.2f, %.2f", playerX, playerY, playerZ));
        }
    }
    
    private void setupInput() {
        inputManager.addMapping("W", new KeyTrigger(KeyInput.KEY_W));
        inputManager.addMapping("S", new KeyTrigger(KeyInput.KEY_S));
        inputManager.addMapping("A", new KeyTrigger(KeyInput.KEY_A));
        inputManager.addMapping("D", new KeyTrigger(KeyInput.KEY_D));
        inputManager.addMapping("SPACE", new KeyTrigger(KeyInput.KEY_SPACE));
        
        inputManager.addMapping("ZoomIn", new MouseAxisTrigger(MouseInput.AXIS_WHEEL, false));
        inputManager.addMapping("ZoomOut", new MouseAxisTrigger(MouseInput.AXIS_WHEEL, true));
        
        inputManager.addListener(new ActionListener() {
            @Override
            public void onAction(String name, boolean isPressed, float tpf) {
                if ("W".equals(name)) wPressed = isPressed;
                if ("S".equals(name)) sPressed = isPressed;
                if ("A".equals(name)) aPressed = isPressed;
                if ("D".equals(name)) dPressed = isPressed;
                if ("SPACE".equals(name)) spacePressed = isPressed;
            }
        }, "W", "S", "A", "D", "SPACE");
        
        inputManager.addListener(new AnalogListener() {
            @Override
            public void onAnalog(String name, float value, float tpf) {
                if ("ZoomIn".equals(name)) {
                    cameraZoom = Math.max(10.0f, cameraZoom - 2.0f);
                }
                if ("ZoomOut".equals(name)) {
                    cameraZoom = Math.min(200.0f, cameraZoom + 2.0f);
                }
            }
        }, "ZoomIn", "ZoomOut");
    }
    
    private void connectToServer() {
        CompletableFuture.runAsync(() -> {
            try {
                networkService = new ClientNetworkService();
                var future = networkService.connect("127.0.0.1", 25564);
                session = future.get(30, TimeUnit.SECONDS);
                
                if (session == null) {
                    log.error("连接服务器失败");
                    return;
                }
                
                log.info("连接成功，开始处理进程切换...");
                handleProcessSwitching();
            } catch (Exception e) {
                log.error("连接服务器异常: {}", e.getMessage(), e);
            }
        });
    }
    
    private void handleProcessSwitching() {
        if (session == null) {
            return;
        }
        
        try {
            Object packet;
            while (!inWorld && (packet = session.receiveNext(5, TimeUnit.SECONDS)) != null) {
                if (packet instanceof PsNVo psNVo) {
                    log.info("收到进程切换通知: 世界={}", psNVo.worldName());
                    aps = psNVo.aps();
                    if (aps > 0) {
                        tickInterval = 1.0 / aps;
                        log.info("已同步服务端物理频率: APS={} TickInterval={}s", aps, String.format("%.4f", tickInterval));
                    }
                    session.sendNext(new PsAllowNDto());
                    session.setStage(ClientNetworkSession.Stage.PROCESS_SWITCHING);
                    continue;
                }
                
                if (packet instanceof PsChunkNVo psChunk) {
                    handleChunkData(psChunk);
                    continue;
                }
                
                if (packet instanceof PsPlayerNVo psPlayer) {
                    handlePlayerData(psPlayer);
                    session.sendNext(new PsFinishNDto());
                    session.setStage(ClientNetworkSession.Stage.PROCESS_SWITCHED);
                    continue;
                }
                
                if (packet instanceof PsJoinWorldNVo) {
                    log.info("已进入世界");
                    synchronized (this) {
                        inWorld = true;
                    }
                    session.setStage(ClientNetworkSession.Stage.IN_WORLD);
                    break;
                }
            }
        } catch (Exception e) {
            log.error("处理进程切换异常: {}", e.getMessage(), e);
        }
    }
    
    private void handleChunkData(PsChunkNVo psChunk) {
        try {
            FlexChunkData chunkData = FlexChunkSerializer.deserialize(psChunk.blockData());
            ChunkPos chunkPos = ChunkPos.of(psChunk.chunkX(), psChunk.chunkZ());
            
            DebugFlexClientChunk chunk = chunkMap.get(chunkPos);
            if (chunk == null) {
                chunk = new DebugFlexClientChunk(psChunk.chunkX(), psChunk.chunkZ());
                chunkMap.put(chunkPos, chunk);
            }
            
            chunk.setFlexChunkData(chunkData);
            markChunkDirty(chunkPos);
            
            log.info("收到区块数据: {} 大小={}x{}x{}", chunkPos, 
                chunkData.getWidth(), chunkData.getHeight(), chunkData.getDepth());
        } catch (Exception e) {
            log.error("处理区块数据异常: {}", e.getMessage(), e);
        }
    }
    
    private void handlePlayerData(PsPlayerNVo psPlayer) {
        playerX = psPlayer.posX();
        playerY = psPlayer.posY();
        playerZ = psPlayer.posZ();
        
        groundAcceleration = psPlayer.ga();
        airAcceleration = psPlayer.aa();
        maxSpeed = psPlayer.ms();
        
        Vector3d newPos = new Vector3d(playerX, playerY, playerZ);
        if (boundingBox == null) {
            boundingBox = new BoundingBox(newPos, 0.6, 1.8);
        } else {
            boundingBox.update(newPos);
        }
        
        log.info("玩家位置: ({}, {}, {}) GA={} AA={} MS={}", playerX, playerY, playerZ, groundAcceleration, airAcceleration, maxSpeed);
        enqueue(() -> updateCamera());
    }
    
    @Override
    public void simpleUpdate(float tpf) {
        if (session == null) {
            return;
        }
        
        boolean isInWorld;
        synchronized (this) {
            isInWorld = inWorld;
        }
        
        if (!isInWorld) {
            return;
        }
        
        // 处理HU数据包
        Object packet;
        while ((packet = session.receiveNext()) != null) {
            if (packet instanceof HuChunkNVo huChunk) {
                handleHuChunkData(huChunk);
            } else if (packet instanceof HuChunkUnloadNVo huUnload) {
                handleHuChunkUnload(huUnload);
            } else if (packet instanceof HuPlayerLocationNVo huLocation) {
                handleHuPlayerLocation(huLocation);
            }
        }
        
        // 处理脏区块队列（每帧限制数量）
        processDirtyChunks();
        
        // 处理完成的网格生成任务
        processMeshGenerationResults();
        
        // 固定时间步长物理更新
        tickAccumulator += tpf;
        while (tickAccumulator >= tickInterval) {
            applyInput(tickInterval);
            updatePhysics(tickInterval);
            clientTick++;
            sendPlayerInput();
            tickAccumulator -= tickInterval;
        }
        
        // 渲染更新（每帧执行）
        updateCamera();
        updatePlayerDot();
        updateCoordsText();
        updateHeightLabels();
    }
    
    private void applyInput(double delta) {
        Vector3d moveDirection = new Vector3d(0, 0, 0);
        
        if (wPressed) moveDirection.z -= 1;
        if (sPressed) moveDirection.z += 1;
        if (aPressed) moveDirection.x -= 1;
        if (dPressed) moveDirection.x += 1;
        
        if (moveDirection.lengthSquared() > 0) {
            moveDirection.normalize();
            
            float acceleration = onGround ? groundAcceleration : airAcceleration;
            velocity.x += moveDirection.x * acceleration * delta;
            velocity.z += moveDirection.z * acceleration * delta;
            
            double hSpeedSq = velocity.x * velocity.x + velocity.z * velocity.z;
            if (hSpeedSq > maxSpeed * maxSpeed) {
                double scale = maxSpeed / Math.sqrt(hSpeedSq);
                velocity.x *= scale;
                velocity.z *= scale;
            }
        }
        
        if (spacePressed && onGround) {
            velocity.y = JUMP_VELOCITY;
            onGround = false;
        }
    }
    
    private void updatePhysics(double delta) {
        if (delta <= 0) {
            return;
        }
        
        double clampedDelta = Math.min(delta, 0.1);
        
        velocity.y += GRAVITY * clampedDelta;
        
        Vector3d movement = new Vector3d(velocity);
        movement.mul(clampedDelta);
        
        Vector3d newPosition = new Vector3d(playerX, playerY, playerZ);
        
        if (boundingBox == null) {
            boundingBox = new BoundingBox(newPosition, 0.6, 1.8);
        }
        
        newPosition.x += movement.x;
        BoundingBox testBox = boundingBox.offset(new Vector3d(movement.x, 0, 0));
        if (!clientWorld.canMoveTo(testBox)) {
            newPosition.x = playerX;
            velocity.x = 0;
        }
        
        newPosition.z += movement.z;
        testBox = boundingBox.offset(new Vector3d(0, 0, movement.z));
        if (!clientWorld.canMoveTo(testBox)) {
            newPosition.z = playerZ;
            velocity.z = 0;
        }
        
        newPosition.y += movement.y;
        testBox = boundingBox.offset(new Vector3d(0, movement.y, 0));
        if (!clientWorld.canMoveTo(testBox)) {
            if (movement.y < 0) {
                onGround = true;
            }
            velocity.y = 0;
            newPosition.y = playerY;
        }
        
        playerX = newPosition.x;
        playerY = newPosition.y;
        playerZ = newPosition.z;
        
        if (boundingBox == null) {
            boundingBox = new BoundingBox(newPosition, 0.6, 1.8);
        } else {
            boundingBox.update(newPosition);
        }
        
        if (clientWorld.canMoveTo(boundingBox)) {
            onGround = false;
        }
        
        if (onGround) {
            velocity.x *= GROUND_FRICTION;
            velocity.z *= GROUND_FRICTION;
        } else {
            velocity.x *= AIR_FRICTION;
            velocity.z *= AIR_FRICTION;
        }
    }

    private void updateHeightLabels() {
        // 回收所有标签
        for (BitmapText label : activeHeightLabels) {
            label.removeFromParent();
            heightLabelPool.add(label);
        }
        activeHeightLabels.clear();
        
        int pX = (int) Math.floor(playerX);
        int pY = (int) Math.floor(playerY);
        int pZ = (int) Math.floor(playerZ);
        
        int range = 6;
        
        for (int x = pX - range; x <= pX + range; x++) {
            for (int z = pZ - range; z <= pZ + range; z++) {
                // 查找该位置最高的固体方块
                int topY = getTopBlockY(x, z, pY);
                if (topY == -999) continue; // 没找到
                
                int diff = topY - pY;
                if (diff > 0) { // 只显示需要跳跃的 (+1 及以上)
                    BitmapText label = getLabelFromPool();
                    label.setText("+" + diff);
                    if (diff == 1) {
                         label.setColor(ColorRGBA.Yellow); // 可以跳
                    } else {
                         label.setColor(ColorRGBA.Red); // 太高，跳不上去
                    }
                    
                    // 放置在方块上方
                    // 旋转 -90 度绕 X 轴，使其躺在地上朝上
                    label.setLocalRotation(new com.jme3.math.Quaternion().fromAngleAxis(-((float)Math.PI / 2), Vector3f.UNIT_X));
                    // 调整位置，0.5 偏移居中，scale 缩放文字大小
                    float scale = 0.05f;
                    label.setLocalScale(scale);
                    label.setLocalTranslation(x + 0.2f, topY + 1.05f, z + 0.8f); 
                    
                    activeHeightLabels.add(label);
                    labelsNode.attachChild(label);
                }
            }
        }
    }
    
    private BitmapText getLabelFromPool() {
        if (heightLabelPool.isEmpty()) {
            BitmapText label = new BitmapText(guiFont);
            label.setSize(guiFont.getCharSet().getRenderedSize());
            return label;
        }
        return heightLabelPool.remove(heightLabelPool.size() - 1);
    }
    
    private int getTopBlockY(int x, int z, int startY) {
        // 从高处往下搜，范围 [startY + 3, startY - 2]
        // 只关心玩家可能跳上去的高度，或者会掉下去的高度
        for (int y = startY + 4; y >= startY - 4; y--) {
            int stateId = clientWorld.getBlockState(x, y, z);
            if (stateId != 0) { // 非空气
                 BlockState state = GlobalPalette.getInstance().getState(stateId);
                 // 忽略流体
                 if (!state.getSharedBlock().isFluid()) {
                     // 确认该方块上方是空气（或者是流体），才能站立
                     int upStateId = clientWorld.getBlockState(x, y + 1, z);
                     if (upStateId == 0) {
                         return y;
                     }
                     BlockState upState = GlobalPalette.getInstance().getState(upStateId);
                     if (upState.getSharedBlock().isFluid()) {
                         return y;
                     }
                 }
            }
        }
        return -999;
    }
    
    private void handleHuPlayerLocation(HuPlayerLocationNVo huLocation) {
        double serverX = huLocation.x();
        double serverY = huLocation.y();
        double serverZ = huLocation.z();
        
        // 计算当前客户端位置和服务端位置的距离平方
        double dx = playerX - serverX;
        double dy = playerY - serverY;
        double dz = playerZ - serverZ;
        double distSq = dx*dx + dy*dy + dz*dz;
        
        // 简单的和解逻辑：如果偏差超过阈值（例如2.0个单位），则强制拉回
        // 否则认为预测成功，继续使用客户端计算的位置
        double reconciliationThreshold = 2.0;
        
        if (distSq > reconciliationThreshold * reconciliationThreshold) {
            log.warn("客户端位置偏差过大 (distSq={}), 强制同步到服务端位置: ({}, {}, {})", 
                String.format("%.2f", distSq), serverX, serverY, serverZ);
            playerX = serverX;
            playerY = serverY;
            playerZ = serverZ;
            velocity.set(0, 0, 0);
            Vector3d newPos = new Vector3d(playerX, playerY, playerZ);
            if (boundingBox == null) {
                boundingBox = new BoundingBox(newPos, 0.6, 1.8);
            } else {
                boundingBox.update(newPos);
            }
        }
    }

    private void handleHuChunkData(HuChunkNVo huChunk) {
        try {
            FlexChunkData chunkData = FlexChunkSerializer.deserialize(huChunk.blockData());
            ChunkPos chunkPos = ChunkPos.of(huChunk.chunkX(), huChunk.chunkZ());
            
            DebugFlexClientChunk chunk = chunkMap.get(chunkPos);
            if (chunk == null) {
                chunk = new DebugFlexClientChunk(huChunk.chunkX(), huChunk.chunkZ());
                chunkMap.put(chunkPos, chunk);
            }
            
            chunk.setFlexChunkData(chunkData);
            markChunkDirty(chunkPos);
            
            log.info("收到热更新区块: {} 大小={}x{}x{}", chunkPos, 
                chunkData.getWidth(), chunkData.getHeight(), chunkData.getDepth());
        } catch (Exception e) {
            log.error("处理热更新区块异常: {}", e.getMessage(), e);
        }
    }
    
    private void handleHuChunkUnload(HuChunkUnloadNVo huUnload) {
        try {
            ChunkPos chunkPos = huUnload.pos();
            DebugFlexClientChunk chunk = chunkMap.remove(chunkPos);
            if (chunk != null) {
                chunk.cleanup();
                log.info("卸载区块: {}", chunkPos);
            }
        } catch (Exception e) {
            log.error("处理区块卸载异常: {}", e.getMessage(), e);
        }
    }
    
    private void markChunkDirty(ChunkPos chunkPos) {
        synchronized (dirtyChunkQueue) {
            if (!dirtyChunkQueue.contains(chunkPos)) {
                dirtyChunkQueue.offer(chunkPos);
            }
        }
    }
    
    private void processDirtyChunks() {
        synchronized (dirtyChunkQueue) {
            int processed = 0;
            while (!dirtyChunkQueue.isEmpty() && processed < MAX_DIRTY_CHUNKS_PER_FRAME) {
                ChunkPos chunkPos = dirtyChunkQueue.poll();
                DebugFlexClientChunk chunk = chunkMap.get(chunkPos);
                
                if (chunk != null && chunk.needsMeshUpdate()) {
                    meshGenerator.submitMeshTask(chunk);
                    processed++;
                }
            }
        }
    }
    
    // 每帧最多应用网格生成的区块数量，防止卡顿
    private static final int MAX_MESH_APPLY_PER_FRAME = 4;
    
    private void processMeshGenerationResults() {
        Iterator<Future<DebugMeshGenerationResult>> it = pendingMeshFutures.iterator();
        int processedCount = 0;
        
        while (it.hasNext()) {
            // 如果本帧处理数量已达上限，暂停处理，留到下一帧
            if (processedCount >= MAX_MESH_APPLY_PER_FRAME) {
                break;
            }
            
            Future<DebugMeshGenerationResult> future = it.next();
            if (future.isDone()) {
                try {
                    DebugMeshGenerationResult result = future.get();
                    ChunkPos chunkPos = ChunkPos.of(result.chunkX, result.chunkZ);
                    DebugFlexClientChunk chunk = chunkMap.get(chunkPos);
                    
                    if (chunk != null) {
                        meshGenerator.applyMeshResult(chunk, result, rootNode3D, assetManager);
                        processedCount++;
                    }
                } catch (Exception e) {
                    log.error("处理网格生成结果异常: {}", e.getMessage(), e);
                }
                it.remove();
            }
        }
        
        // 添加新的待处理任务
        List<Future<DebugMeshGenerationResult>> newFutures = meshGenerator.getPendingFutures();
        for (Future<DebugMeshGenerationResult> future : newFutures) {
            if (!pendingMeshFutures.contains(future)) {
                pendingMeshFutures.add(future);
            }
        }
    }
    
    private void sendPlayerInput() {
        if (session == null) {
            return;
        }
        
        boolean isInWorld;
        synchronized (this) {
            isInWorld = inWorld;
        }
        
        if (!isInWorld) {
            return;
        }
        
        boolean isAnyKeyPressed = wPressed || sPressed || aPressed || dPressed || spacePressed;
        
        // 如果当前没有按键，且上一次也没有按键（即处于静止状态），则不发送数据包
        // 这样可以避免在静止时大量发送全false的数据包
        if (!isAnyKeyPressed && !lastInputWasActive) {
            return;
        }
        
        PlayerInputStateNDto input = new PlayerInputStateNDto(
            clientTick,
            wPressed,
            sPressed,
            aPressed,
            dPressed,
            spacePressed,
            false,
            0.0f,
            0.0f
        );
        session.sendNext(input);
        
        lastInputWasActive = isAnyKeyPressed;
    }
    
    private void updatePlayerDot() {
        Geometry playerDot = (Geometry) rootNode3D.getChild("PlayerDot");
        if (playerDot == null) {
            // 创建玩家红点
            playerDot = new Geometry("PlayerDot", new Quad(0.5f, 0.5f));
            Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
            mat.setColor("Color", ColorRGBA.Red);
            playerDot.setMaterial(mat);
            playerDot.rotate(-(float)Math.PI / 2, 0, 0);
            rootNode3D.attachChild(playerDot);
        }
        
        playerDot.setLocalTranslation((float)playerX - 0.25f, (float)playerY + 1.1f, (float)playerZ + 0.25f);
    }
    
    // 简化的World实现，用于网格生成器
    private class DebugClientWorld {
        public int getBlockState(int x, int y, int z) {
            int chunkX = (int) Math.floor(x / (double) CHUNK_SIZE_X);
            int chunkZ = (int) Math.floor(z / (double) CHUNK_SIZE_Z);
            ChunkPos chunkPos = ChunkPos.of(chunkX, chunkZ);
            DebugFlexClientChunk chunk = chunkMap.get(chunkPos);
            
            if (chunk == null) {
                return 0; // Air
            }
            
            int localX = x - chunkX * CHUNK_SIZE_X;
            int localZ = z - chunkZ * CHUNK_SIZE_Z;
            
            return chunk.getBlockStateId(localX, y, localZ);
        }
        
        public boolean canMoveTo(BoundingBox box) {
            int minX = (int) Math.floor(box.getMinX());
            int maxX = (int) Math.floor(box.getMaxX());
            int minY = (int) Math.floor(box.getMinY());
            int maxY = (int) Math.floor(box.getMaxY());
            int minZ = (int) Math.floor(box.getMinZ());
            int maxZ = (int) Math.floor(box.getMaxZ());
            
            GlobalPalette palette = GlobalPalette.getInstance();
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        int stateId = getBlockState(x, y, z);
                        if (stateId == 0) {
                            continue; // 空气或未加载区块，假设可以移动
                        }
                        BlockState state = palette.getState(stateId);
                        if (state == null) {
                            continue;
                        }
                        if (state.getSharedBlock().isSolid()) {
                            return false;
                        }
                    }
                }
            }
            return true;
        }
    }
    
    // 简化的SharedWorld实现，用于调试客户端
    // private static class DebugSharedWorld implements SharedWorld {
    //    @Override
    //    public boolean isServerSide() {
    //        return false;
    //    }
    //    
    //    @Override
    //    public boolean isClientSide() {
    //        return true;
    //    }
    //    
    //    @Override
    //    public void action(double delta) {
    //        // 调试客户端不需要实现世界逻辑
    //    }
    // }
    
    public static void main(String[] args) {
        DebugClient app = new DebugClient();
        
        AppSettings settings = new AppSettings(true);
        settings.setTitle("MyCraft Debug Client");
        settings.setWidth(800);
        settings.setHeight(600);
        settings.setVSync(true);
        app.setSettings(settings);
        
        app.start();
    }
}
