package com.ksptool.ourcraft.server.world.chunk;

import com.ksptool.ourcraft.sharedcore.GlobalPalette;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * 区块序列化器，负责区块对象和字节数组之间的转换
 * 序列化流程：
 * 1. 创建局部调色板（只包含该区块使用的方块状态）
 * 2. 使用游程编码（RLE）压缩连续相同的方块
 * 3. 使用 zlib 压缩数据
 */
public class SimpleChunkSerializer {

    //区块大小
    private static final int CHUNK_SIZE = SimpleServerChunk.CHUNK_SIZE;

    //区块高度
    private static final int CHUNK_HEIGHT = SimpleServerChunk.CHUNK_HEIGHT;
    
    public static byte[] serialize(SimpleServerChunk chunk) throws IOException {
        GlobalPalette globalPalette = GlobalPalette.getInstance();
        
        Map<Integer, Integer> localPalette = new HashMap<>();
        List<Integer> paletteList = new ArrayList<>();
        
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int y = 0; y < CHUNK_HEIGHT; y++) {
                for (int z = 0; z < CHUNK_SIZE; z++) {
                    int globalStateId = chunk.getBlockStateId(x, y, z);
                    if (!localPalette.containsKey(globalStateId)) {
                        localPalette.put(globalStateId, paletteList.size());
                        paletteList.add(globalStateId);
                    }
                }
            }
        }
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        
        dos.writeInt(paletteList.size());
        for (Integer globalStateId : paletteList) {
            dos.writeInt(globalStateId);
        }
        
        List<RleEntry> rleData = new ArrayList<>();
        int currentLocalId = localPalette.get(chunk.getBlockState(0, 0, 0));
        int currentCount = 1;
        
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int y = 0; y < CHUNK_HEIGHT; y++) {
                for (int z = 0; z < CHUNK_SIZE; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }
                    
                    int state = chunk.getBlockStateId(x, y, z);
                    int localId = localPalette.get(state);
                    
                    if (localId == currentLocalId && currentCount < 32767) {
                        currentCount++;
                    } else {
                        rleData.add(new RleEntry(currentLocalId, currentCount));
                        currentLocalId = localId;
                        currentCount = 1;
                    }
                }
            }
        }
        rleData.add(new RleEntry(currentLocalId, currentCount));
        
        dos.writeInt(rleData.size());
        for (RleEntry entry : rleData) {
            dos.writeShort(entry.localId);
            dos.writeShort(entry.count);
        }
        
        byte[] uncompressed = baos.toByteArray();
        
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(uncompressed);
        deflater.finish();
        
        ByteArrayOutputStream compressedBaos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            compressedBaos.write(buffer, 0, count);
        }
        deflater.end();
        
        return compressedBaos.toByteArray();
    }
    
    public static SimpleServerChunk deserialize(byte[] compressedData, int chunkX, int chunkZ) throws IOException {
        Inflater inflater = new Inflater();
        inflater.setInput(compressedData);
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        try {
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                baos.write(buffer, 0, count);
            }
        } catch (DataFormatException e) {
            throw new IOException("解压区块数据失败: " + e.getMessage(), e);
        } finally {
            inflater.end();
        }
        
        byte[] uncompressed = baos.toByteArray();
        DataInputStream dis = new DataInputStream(new java.io.ByteArrayInputStream(uncompressed));
        
        int paletteSize = dis.readInt();
        List<Integer> paletteList = new ArrayList<>();
        for (int i = 0; i < paletteSize; i++) {
            paletteList.add(dis.readInt());
        }
        
        int rleSize = dis.readInt();
        List<RleEntry> rleData = new ArrayList<>();
        for (int i = 0; i < rleSize; i++) {
            int localId = dis.readShort() & 0xFFFF;
            int count = dis.readShort() & 0xFFFF;
            rleData.add(new RleEntry(localId, count));
        }
        
        SimpleServerChunk chunk = new SimpleServerChunk(chunkX, chunkZ);
        
        int index = 0;
        for (RleEntry entry : rleData) {
            int globalStateId = paletteList.get(entry.localId);
            for (int i = 0; i < entry.count && index < CHUNK_SIZE * CHUNK_HEIGHT * CHUNK_SIZE; i++) {
                int z = index % CHUNK_SIZE;
                int y = (index / CHUNK_SIZE) % CHUNK_HEIGHT;
                int x = index / (CHUNK_SIZE * CHUNK_HEIGHT);
                chunk.setBlockState(x, y, z, globalStateId);
                index++;
            }
        }
        
        chunk.setState(SimpleServerChunk.ChunkState.DATA_LOADED);
        chunk.markDirty(false);
        return chunk;
    }
    
    private static class RleEntry {
        final int localId;
        final int count;
        
        RleEntry(int localId, int count) {
            this.localId = localId;
            this.count = count;
        }
    }
}

