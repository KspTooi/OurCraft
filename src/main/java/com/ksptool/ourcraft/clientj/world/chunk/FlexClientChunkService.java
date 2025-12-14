package com.ksptool.ourcraft.clientj.world.chunk;

import com.ksptool.ourcraft.clientj.OurCraftClientJ;
import com.ksptool.ourcraft.clientj.world.ClientWorld;
import com.ksptool.ourcraft.sharedcore.utils.FlexChunkData;
import com.ksptool.ourcraft.sharedcore.utils.FlexChunkSerializer;
import com.ksptool.ourcraft.sharedcore.utils.position.ChunkPos;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    public FlexClientChunkService(OurCraftClientJ client, ClientWorld world) {

        this.client = client;
        this.world = world;

        //从世界模板中获取区块大小
        var template = world.getTemplate();
        this.chunkSizeX = template.getChunkSizeX();
        this.chunkSizeZ = template.getChunkSizeZ();
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

}
