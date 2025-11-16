package com.ksptool.ourcraft.server.world.save;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 区域文件管理器，用于读写 .sca 和 .sce 文件
 * 文件格式：
 * - Magic Number (4 bytes)
 * - Version (1 byte)
 * - Index Table (40 * 40 * 8 bytes): 每个条目包含 Offset (4 bytes) 和 Length (4 bytes)
 * - Data Section: 压缩后的数据块
 */
public class RegionFile {
    private static final int REGION_SIZE = 40;
    private static final int HEADER_SIZE = 4 + 1 + (REGION_SIZE * REGION_SIZE * 8);
    private static final int INDEX_ENTRY_SIZE = 8;
    
    private final File file;
    private final String magicNumber;
    private RandomAccessFile raf;
    private boolean dirty;
    
    public RegionFile(File file, String magicNumber) {
        this.file = file;
        this.magicNumber = magicNumber;
        this.dirty = false;
    }
    
    public void open() throws IOException {
        if (raf != null) {
            return;
        }
        
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            file.createNewFile();
            initializeFile();
        }
        
        raf = new RandomAccessFile(file, "rw");
        verifyMagicNumber();
    }
    
    public void close() throws IOException {
        if (raf != null) {
            raf.close();
            raf = null;
        }
    }
    
    private void initializeFile() throws IOException {
        try (RandomAccessFile tempRaf = new RandomAccessFile(file, "rw")) {
            byte[] header = new byte[HEADER_SIZE];
            ByteBuffer buffer = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN);
            
            buffer.put(magicNumber.getBytes("ASCII"), 0, 4);
            buffer.put((byte) 1);
            
            for (int i = 0; i < REGION_SIZE * REGION_SIZE; i++) {
                buffer.putInt(0);
                buffer.putInt(0);
            }
            
            tempRaf.write(header);
        }
    }
    
    private void verifyMagicNumber() throws IOException {
        raf.seek(0);
        byte[] magic = new byte[4];
        raf.readFully(magic);
        String fileMagic = new String(magic, "ASCII");
        if (!fileMagic.equals(magicNumber)) {
            throw new IOException("Invalid magic number: expected " + magicNumber + ", got " + fileMagic);
        }
    }
    
    public byte[] readChunk(int localX, int localZ) throws IOException {
        if (raf == null) {
            open();
        }
        
        if (localX < 0 || localX >= REGION_SIZE || localZ < 0 || localZ >= REGION_SIZE) {
            return null;
        }
        
        int index = localZ * REGION_SIZE + localX;
        long indexOffset = 5 + (index * INDEX_ENTRY_SIZE);
        
        raf.seek(indexOffset);
        int offset = raf.readInt();
        int length = raf.readInt();
        
        if (offset == 0 || length == 0) {
            return null;
        }
        
        raf.seek(offset);
        byte[] data = new byte[length];
        raf.readFully(data);
        
        return data;
    }
    
    public void writeChunk(int localX, int localZ, byte[] data) throws IOException {
        if (raf == null) {
            open();
        }
        
        if (localX < 0 || localX >= REGION_SIZE || localZ < 0 || localZ >= REGION_SIZE) {
            return;
        }
        
        if (data == null || data.length == 0) {
            return;
        }
        
        int index = localZ * REGION_SIZE + localX;
        long indexOffset = 5 + (index * INDEX_ENTRY_SIZE);
        
        raf.seek(indexOffset);
        int oldOffset = raf.readInt();
        int oldLength = raf.readInt();
        
        int newOffset;
        if (oldOffset > 0 && oldLength > 0 && data.length <= oldLength) {
            newOffset = oldOffset;
            raf.seek(newOffset);
            raf.write(data);
            if (data.length < oldLength) {
                raf.write(new byte[oldLength - data.length]);
            }
        } else {
            long fileLength = raf.length();
            newOffset = (int) fileLength;
            raf.seek(newOffset);
            raf.write(data);
        }
        
        raf.seek(indexOffset);
        raf.writeInt(newOffset);
        raf.writeInt(data.length);
        raf.getFD().sync();
        
        dirty = true;
    }
    
    public boolean isDirty() {
        return dirty;
    }
    
    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }
    
    public File getFile() {
        return file;
    }
}

