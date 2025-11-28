package com.ksptool.ourcraft.server.world.chunk;

import com.ksptool.ourcraft.sharedcore.utils.BitStorage;
import com.ksptool.ourcraft.sharedcore.utils.CompactBlockData;
import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * 数据结构
 * 1B：版本号
 * 4B：区块大小X
 * 4B：区块大小Y
 * 4B：区块大小Z
 * 4B：调色板种类数量
 * N * 8B：调色板数据
 * 4B：存储位数(对于每个块来说，需要多少位来存储)
 * 4B：块数组长度
 * N * 8B：块存储数据
 */
public class SuperChunkSerializer {

    /**
     * 序列化超级区块
     * @param cbd 超级区块
     * @return 序列化后的字节数组
     */
    public static byte[] serialize(CompactBlockData cbd){

        //创建CompactBlockData的快照
        CompactBlockData.Snapshot cbdSnapshot = cbd.createSnapshot();

        //创建CompactBlockData的快照
        IntArrayList palette = cbdSnapshot.getLocalPalette();
        BitStorage storage = cbdSnapshot.getStorage();
        long[] blockData = storage.getData(); //块存储数据

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(4096)){

            DataOutputStream dos = new DataOutputStream(baos);

            //写入头部
            dos.write(1); //1B 版本
            dos.writeInt(cbd.getWidth());  //4B 大小X
            dos.writeInt(cbd.getHeight());  //4B 大小Y
            dos.writeInt(cbd.getDepth());  //4B 大小Z

            //写入调色板
            dos.writeInt(palette.size()); //4B 调色板种类数量

            for (int i = 0; i < palette.size(); i++) {
                dos.writeInt(palette.getInt(i)); // 种类数量 * 4B
            }

            //写入块存储数据
            dos.writeInt(storage.getBitsPerEntry()); //4B 存储位数(对于每个块来说，需要多少位来存储)
            dos.writeInt(blockData.length); //4B 块数组长度
            for (long l : blockData) {
                dos.writeLong(l); // N * 8B
            }

            return baos.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 反序列化超级区块
     * @param src 序列化后的字节数组
     * @return 反序列化后的超级区块
     */
    public static CompactBlockData deserialize(byte[] src){

        if (src == null || src.length == 0){
            throw new IllegalArgumentException("Deserialize data is null or empty");
        }

        try (ByteArrayInputStream bais = new ByteArrayInputStream(src)){

            DataInputStream dis = new DataInputStream(bais);

            var version = dis.read(); //1B 版本
            var width = dis.readInt(); //4B 大小X
            var height = dis.readInt(); //4B 大小Y
            var depth = dis.readInt(); //4B 大小Z

            //校验版本
            if (version != 1){
                throw new IllegalArgumentException("Unsupported version: " + version);
            }

            //读调色板
            var paletteSize = dis.readInt(); //4B 调色板种类数量
            var palette = new IntArrayList(paletteSize);
            for (int i = 0; i < paletteSize; i++) {
                palette.add(dis.readInt()); // N * 4B
            }

            //读块存储数据
            var bitsPerEntry = dis.readInt(); //4B 存储位数(对于每个块来说，需要多少位来存储)
            var arrLength = dis.readInt(); //4B 块数组长度
            var blockData = new long[arrLength];
            for (int i = 0; i < arrLength; i++) {
                blockData[i] = dis.readLong(); // N * 8B
            }

        
            return new CompactBlockData(width, height, depth, bitsPerEntry, blockData);

        } catch (IOException e) {
            throw new RuntimeException("Deserialize failed", e);
        }

    }

}

