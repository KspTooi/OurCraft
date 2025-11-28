package com.ksptool.ourcraft.server.archive;

import com.ksptool.ourcraft.sharedcore.utils.ChunkUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 超级区块归档文件 (Super Chunk Archive File) - 核心 I/O 句柄
 * 该类负责直接操作底层的 .sca 文件，实现了针对游戏存档的高性能、崩溃一致性 (Crash-Consistent) 读写。
 * 它采用了类似日志结构文件系统 (LFS) 的写入策略，以牺牲一定的磁盘空间为代价，换取数据安全性。
 * 
 * 追加写入：无论新旧数据大小对比如何，新数据永远写入文件末尾 (EOF)
 * 强制落盘: 在修改索引指针前，强制操作系统将数据刷入物理磁盘。
 * 原子指针更新：只有当数据安全后，才修改文件头的 Offset 指针指向新位置。
 * 若在写入过程中崩溃，文件末尾会残留垃圾数据，但索引表依然指向旧的、完好的数据，存档本身不受影响。
 * 
 * 维护：碎片整理 (Compaction)
 * 由于追加写策略会导致文件产生"空洞"（旧数据的废弃空间），导致文件体积单调增长。
 * 必须定期（如服务器关闭或闲置时）调用 compact 方法。该方法会执行碎片整理 重建一个新的紧凑文件并原子替换旧文件。
 * 
 * SCA文件格式规范：
 * - Magic Number (4 bytes)
 * - Version (1 byte)
 * - Index Table (40 * 40 * 8 bytes): 每个条目包含 Offset (4 bytes) 和 Length (4 bytes)
 * - Data Section: 压缩后的数据块
 * 4B 1B 12800B 数据块
 */
@Slf4j
public class SuperChunkArchiveFile {

    public static final int SCAF_CHUNK_SIZE = 40;
    private static final int HEADER_SIZE = 4 + 1 + (SCAF_CHUNK_SIZE * SCAF_CHUNK_SIZE * 8);
    private static final int INDEX_ENTRY_SIZE = 8;

    //SCA文件
    private final Path path;

    //魔数 默认值为SCAF
    private String magicNumber = "SCAF";

    //文件句柄
    private RandomAccessFile raf;


    //是否脏了
    @Setter
    @Getter
    private boolean dirty;

    public SuperChunkArchiveFile(Path path){
        this.path = path;
        this.dirty = false;
    }

    /**
     * 允许自定义魔数（用于兼容SCE等其他格式）
     */
    public SuperChunkArchiveFile(Path path, String magicNumber){
        this.path = path;
        this.magicNumber = magicNumber;
        this.dirty = false;
    }

    /**
     * 打开文件
     */
    public void open() throws IOException {
        if (raf != null) {
            return;
        }

        if (!Files.exists(path)) {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.createFile(path);
            initializeFile();
        }

        raf = new RandomAccessFile(path.toFile(), "rw");
        verifyMagicNumber();
    }

    /**
     * 关闭文件
     */
    public void close() throws IOException {
        if (raf != null) {
            raf.close();
            raf = null;
        }
    }

    /**
     * 初始化文件 创建文件并写入魔数和版本号
     */
    private void initializeFile() throws IOException {
        try (RandomAccessFile tempRaf = new RandomAccessFile(path.toFile(), "rw")) {
            byte[] header = new byte[HEADER_SIZE];
            ByteBuffer buffer = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN);

            buffer.put(magicNumber.getBytes(StandardCharsets.US_ASCII), 0, 4);
            buffer.put((byte) 1);

            for (int i = 0; i < SCAF_CHUNK_SIZE * SCAF_CHUNK_SIZE; i++) {
                buffer.putInt(0);
                buffer.putInt(0);
            }

            tempRaf.write(header);
        }
    }

    /**
     * 验证魔数
     */
    private void verifyMagicNumber() throws IOException {
        raf.seek(0);
        byte[] magic = new byte[4];
        raf.readFully(magic);
        String fileMagic = new String(magic, StandardCharsets.US_ASCII);
        if (!fileMagic.equals(magicNumber)) {
            throw new IOException("Invalid magic number: expected " + magicNumber + ", got " + fileMagic);
        }
    }

    /**
     * 判断区块是否存在于归档中
     * @param chunkX 区块X坐标
     * @param chunkZ 区块Z坐标
     * @return 是否存在
     */
    public boolean hasChunk(int chunkX, int chunkZ) throws IOException {
        if (raf == null) {
            open();
        }

        var localX = ChunkUtils.getLocalChunkX(chunkX);
        var localZ = ChunkUtils.getLocalChunkZ(chunkZ);

        if (localX < 0 || localX >= SCAF_CHUNK_SIZE || localZ < 0 || localZ >= SCAF_CHUNK_SIZE) {
            return false;
        }

        int index = localZ * SCAF_CHUNK_SIZE + localX;
        long indexOffset = 5 + (index * INDEX_ENTRY_SIZE);

        raf.seek(indexOffset);
        int offset = raf.readInt();
        int length = raf.readInt();

        return offset != 0 && length != 0;
    }

    /**
     * 读取区块数据
     * @param chunkX 区块X坐标
     * @param chunkZ 区块Z坐标
     * @return 区块数据
     */
    public byte[] readChunk(int chunkX, int chunkZ) throws IOException {
        if (raf == null) {
            open();
        }

        var localX = ChunkUtils.getLocalChunkX(chunkX);
        var localZ = ChunkUtils.getLocalChunkZ(chunkZ);

        if (localX < 0 || localX >= SCAF_CHUNK_SIZE || localZ < 0 || localZ >= SCAF_CHUNK_SIZE) {
            return null;
        }

        int index = localZ * SCAF_CHUNK_SIZE + localX;
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

    /**
     * 写入区块数据 (原子写实现)
     * 采用 Append-Only 模式：总是将新数据写入文件末尾，而不是覆盖旧数据。
     * 这保证了如果写入过程中断电，旧数据依然完好。
     *
     * @param chunkX 区块X坐标
     * @param chunkZ 区块Z坐标
     * @param data 区块数据
     */
    public void writeChunk(int chunkX, int chunkZ, byte[] data) throws IOException {

        var localX = ChunkUtils.getLocalChunkX(chunkX);
        var localZ = ChunkUtils.getLocalChunkZ(chunkZ);

        // 默认开启 sync，保证原子性
        writeChunk(localX, localZ, data, true);
        log.info("保存区块 [{},{}] SCA本地坐标 [{},{}] SCA文件 {}", chunkX, chunkZ, localX, localZ, path.getFileName().toString());
    }

    /**
     * 内部写入实现
     * @param sync 是否强制刷盘（fsync）。常规写入必须为 true，碎片整理时可设为 false 以提高性能。
     */
    private void writeChunk(int localX, int localZ, byte[] data, boolean sync) throws IOException {
        if (raf == null) {
            open();
        }

        if (localX < 0 || localX >= SCAF_CHUNK_SIZE || localZ < 0 || localZ >= SCAF_CHUNK_SIZE) {
            return;
        }

        if (data == null || data.length == 0) {
            return;
        }

        int index = localZ * SCAF_CHUNK_SIZE + localX;
        long indexOffset = 5 + (index * INDEX_ENTRY_SIZE);

        //获取文件当前末尾位置
        long newOffset = raf.length();

        //移动到末尾并写入数据 (Append)
        //无论旧数据空间是否足够，都追加到末尾，避免原地覆盖造成的潜在损坏
        raf.seek(newOffset);
        raf.write(data);

        //强制数据落盘 (Crash Consistency Step 1)
        //确保数据区已安全写入，此时索引尚未更新，若崩溃则新数据只是文件末尾的垃圾数据，不影响存档有效性
        if (sync) {
            raf.getChannel().force(true);
        }

        //更新索引表
        raf.seek(indexOffset);
        raf.writeInt((int) newOffset);
        raf.writeInt(data.length);

        //强制索引落盘 (Crash Consistency Step 2)
        //确保索引指向新数据，原子性完成
        if (sync) {
            raf.getChannel().force(true);
        }

        dirty = true;
    }

    /**
     * 碎片整理 (Compaction)
     * 由于采用追加写模式，文件会产生空洞（旧数据的空间）。
     * 此方法将创建一个临时文件，将所有有效区块紧凑地写入，然后原子替换原文件。
     * 建议在服务器关闭或闲置时调用。
     */
    public void compact() throws IOException {
        if (raf == null) {
            open();
        }

        // 创建临时文件路径: filename.sca.tmp
        Path tempPath = path.resolveSibling(path.getFileName().toString() + ".tmp");
        Files.deleteIfExists(tempPath);

        // 使用相同的魔数创建临时文件处理器
        SuperChunkArchiveFile tempFile = new SuperChunkArchiveFile(tempPath, this.magicNumber);
        tempFile.open();

        try {
            // 遍历所有可能的区块索引
            for (int x = 0; x < SCAF_CHUNK_SIZE; x++) {
                for (int z = 0; z < SCAF_CHUNK_SIZE; z++) {
                    // 从当前文件读取有效数据
                    byte[] data = this.readChunk(x, z);
                    if (data != null && data.length > 0) {
                        // 写入临时文件 (sync=false 以提高速度，最后统一刷盘)
                        tempFile.writeChunk(x, z, data, false);
                    }
                }
            }

            // 确保所有数据落盘
            if (tempFile.raf != null) {
                tempFile.raf.getChannel().force(true);
            }

        } finally {
            // 关闭两个文件以释放句柄
            tempFile.close();
            this.close();
        }

        // 原子替换 (Atomic Move)
        // 如果在此步骤前崩溃，原文件不受影响，仅残留tmp文件
        // 如果在此步骤后崩溃，原文件已被新文件替换
        Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

        // 重新打开文件
        this.open();
    }

    /**
     * 获取文件
     * @return 文件
     */
    public File getFile() {
        return path.toFile();
    }

}