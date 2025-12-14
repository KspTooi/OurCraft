package com.ksptool.ourcraft.clientj.world.chunk;

import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import com.ksptool.ourcraft.clientj.OurCraftClientJ;
import com.ksptool.ourcraft.clientj.world.ClientWorld;
import com.ksptool.ourcraft.clientj.world.mesh.FlexChunkMeshGenerator;
import com.ksptool.ourcraft.clientj.world.mesh.MeshGenerationResult;
import com.ksptool.ourcraft.sharedcore.utils.FlexChunkData;
import com.ksptool.ourcraft.sharedcore.utils.FlexChunkSerializer;
import com.ksptool.ourcraft.sharedcore.utils.position.ChunkPos;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.*;

import org.apache.commons.lang3.StringUtils;

@Getter
@Slf4j
public class FlexClientChunkService {

    //服务器实例
    private final OurCraftClientJ client;

    //世界实例
    private final ClientWorld world;

    //Flex区块->Flex区块
    private final Map<ChunkPos, FlexClientChunk> chunks = new ConcurrentHashMap<>();

    //区块大小X
    private final int chunkSizeX;

    //区块大小Z
    private final int chunkSizeZ;

    //网格生成器
    private final FlexChunkMeshGenerator meshGenerator;

    //线程池
    private final ExecutorService executor;

    //待处理的Future
    private final Map<ChunkPos, Future<MeshGenerationResult>> pendingFutures = new ConcurrentHashMap<>();

    public FlexClientChunkService(OurCraftClientJ client, ClientWorld world) {

        this.client = client;
        this.world = world;

        //从世界模板中获取区块大小
        var template = world.getTemplate();
        this.chunkSizeX = template.getChunkSizeX();
        this.chunkSizeZ = template.getChunkSizeZ();

        //创建网格生成器
        this.meshGenerator = new FlexChunkMeshGenerator(world);

        //创建线程池
        int numThreads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        this.executor = Executors.newFixedThreadPool(numThreads);
    }

    /**
     * 添加区块数据 
     * @param chunkX 区块X坐标
     * @param chunkZ 区块Z坐标
     * @param data 未反序列化的原始区块数据
     */
    public void addChunkFromRawData(ChunkPos chunkPos, byte[] data) {
        FlexChunkData chunkData = FlexChunkSerializer.deserialize(data);
        FlexClientChunk chunk = new FlexClientChunk(chunkPos, chunkData);
        chunk.setStage(FlexClientChunk.Stage.NEED_MESH_UPDATE);
        chunks.put(chunkPos, chunk);
        log.info("添加客户端区块: ({})", chunkPos);
    }

    /**
     * 获取区块
     * @param chunkPos 区块坐标
     * @return 区块，如果不存在返回null
     */
    public FlexClientChunk getChunk(ChunkPos chunkPos) {
        return chunks.get(chunkPos);
    }

    /**
     * 更新方法，在主线程中调用
     * 提交脏区块的MESH计算任务，并处理已完成的MESH结果
     * @param tpf 每帧时间
     */
    public void update(float tpf) {
        //提交需要更新的区块
        for (FlexClientChunk chunk : chunks.values()) {
            if (chunk.getStage() == FlexClientChunk.Stage.NEED_MESH_UPDATE) {
                ChunkPos pos = chunk.getChunkPos();
                if (pendingFutures.containsKey(pos)) {
                    continue;
                }
                
                chunk.setStage(FlexClientChunk.Stage.MESH_GENERATING);
                Callable<MeshGenerationResult> task = () -> meshGenerator.calculateMeshData(chunk, world);
                Future<MeshGenerationResult> future = executor.submit(task);
                pendingFutures.put(pos, future);
            }
        }

        //处理已完成的Future
        Iterator<Map.Entry<ChunkPos, Future<MeshGenerationResult>>> iterator = pendingFutures.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ChunkPos, Future<MeshGenerationResult>> entry = iterator.next();
            ChunkPos pos = entry.getKey();
            Future<MeshGenerationResult> future = entry.getValue();
            
            if (!future.isDone()) {
                continue;
            }
            
            iterator.remove();
            
            try {
                MeshGenerationResult result = future.get();
                FlexClientChunk chunk = chunks.get(pos);
                if (chunk == null || chunk.getStage() == FlexClientChunk.Stage.INVALID) {
                    continue;
                }
                
                //在主线程中创建JME Mesh和Geometry
                client.enqueue(() -> {
                    applyMeshResult(chunk, result);
                    chunk.setStage(FlexClientChunk.Stage.READY);
                });
            } catch (Exception e) {
                log.error("处理区块MESH结果失败: {}", pos, e);
                FlexClientChunk chunk = chunks.get(pos);
                if (chunk != null) {
                    chunk.setStage(FlexClientChunk.Stage.NEED_MESH_UPDATE);
                }
            }
        }
    }

    /**
     * 应用MESH结果到JME Geometry
     */
    private void applyMeshResult(FlexClientChunk chunk, MeshGenerationResult result) {
        //移除旧的Geometry
        if (chunk.getGeometry() != null) {
            chunk.getGeometry().removeFromParent();
        }

        if (result.vertices.length == 0) {
            return;
        }

        //创建JME Mesh
        Mesh mesh = createJmeMesh(result.vertices, result.texCoords, result.tints, result.indices);
        Geometry geometry = new Geometry("Chunk_" + result.chunkX + "_" + result.chunkZ, mesh);
        
        //设置位置
        geometry.setLocalTranslation(result.chunkX * chunkSizeX, 0, result.chunkZ * chunkSizeZ);
        
        //创建绿色材质
        Material material = new Material(client.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        material.setBoolean("VertexColor", true);
        material.setColor("Color", ColorRGBA.White);
        geometry.setMaterial(material);
        
        chunk.setGeometry(geometry);
        world.getWorldNode().attachChild(geometry);
    }

    /**
     * 创建JME Mesh
     */
    private Mesh createJmeMesh(float[] vertices, float[] texCoords, float[] colors, int[] indices) {
        Mesh mesh = new Mesh();
        
        //设置顶点位置
        FloatBuffer vertexBuffer = BufferUtils.createFloatBuffer(vertices);
        mesh.setBuffer(VertexBuffer.Type.Position, 3, vertexBuffer);
        
        //设置纹理坐标
        if (texCoords.length > 0) {
            FloatBuffer texCoordBuffer = BufferUtils.createFloatBuffer(texCoords);
            mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, texCoordBuffer);
        }
        
        //设置顶点颜色
        if (colors.length > 0) {
            FloatBuffer colorBuffer = BufferUtils.createFloatBuffer(colors);
            mesh.setBuffer(VertexBuffer.Type.Color, 4, colorBuffer);
        }
        
        //设置索引
        IntBuffer indexBuffer = BufferUtils.createIntBuffer(indices);
        mesh.setBuffer(VertexBuffer.Type.Index, 3, indexBuffer);
        
        mesh.updateBound();
        return mesh;
    }

    /**
     * 关闭服务
     */
    public void shutdown() {
        executor.shutdown();
    }

}
