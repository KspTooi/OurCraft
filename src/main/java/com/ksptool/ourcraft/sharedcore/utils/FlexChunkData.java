package com.ksptool.ourcraft.sharedcore.utils;

import com.ksptool.ourcraft.sharedcore.enums.BlockEnums;
import com.ksptool.ourcraft.sharedcore.blocks.inner.SharedBlock;
import com.ksptool.ourcraft.sharedcore.world.BlockState;
import com.ksptool.ourcraft.sharedcore.GlobalPalette;
import com.ksptool.ourcraft.sharedcore.Registry;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FlexChunkData {

    @Getter
    private final int width;  // X轴

    @Getter
    private final int height; // Y轴

    @Getter
    private final int depth;  // Z轴

    private final int totalSize; // 总方块数 (w * h * d)

    //预计算层面积 (width * depth)，用于加速索引计算
    private final int layerArea;

    //区块中的方块数据(使用压缩的Long数组)
    @Getter
    private volatile BitStorage storage;

    //本地调色板
    @Getter
    private final IntArrayList localPalette;

    //全局ID到局部ID的映射
    @Getter
    private final Int2IntOpenHashMap globalToLocal;

    //非空气方块数量
    private int nonAirBlockCount = 0;

    //缓存的空气方块全局ID
    private static int CACHED_AIR_GLOBAL_ID = -1;

    //锁对象
    private final Object lock = new Object();

    /**
     * 构造函数
     * @param x 区块X尺寸
     * @param y 区块Y尺寸
     * @param z 区块Z尺寸
     * @param bitsPerEntry 每个块需要多少位来存储
     * @param data 数据数组
     */
    public FlexChunkData(int x, int y, int z, int bitsPerEntry, long[] data, IntArrayList palette) {
        this.width = x;
        this.height = y;
        this.depth = z;
        this.layerArea = x * z;
        this.totalSize = this.layerArea * y;
        this.localPalette = palette;
        this.globalToLocal = new Int2IntOpenHashMap();
        this.globalToLocal.defaultReturnValue(-1);
        this.storage = new BitStorage(x, y, z, bitsPerEntry, data);

        //初始化空气
        int airGlobalId = getAirGlobalId();
        localPalette.add(airGlobalId);
        globalToLocal.put(airGlobalId, 0);
    }


    /**
     * 构造支持任意尺寸的区块数据
     *
     * @param width  X轴大小
     * @param height Y轴大小
     * @param depth  Z轴大小
     */
    public FlexChunkData(int width, int height, int depth) {
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.layerArea = width * depth;
        this.totalSize = this.layerArea * height;

        this.localPalette = new IntArrayList();
        this.globalToLocal = new Int2IntOpenHashMap();
        this.globalToLocal.defaultReturnValue(-1);

        // 根据动态计算的总大小初始化存储
        this.storage = new BitStorage(4, totalSize);

        //初始化空气
        int airGlobalId = getAirGlobalId();
        localPalette.add(airGlobalId);
        globalToLocal.put(airGlobalId, 0);
    }


    public BlockState getBlock(int x, int y, int z) {
        //需在调用层做坐标范围检查，这里为了极致性能可省略
        int index = getIndex(x, y, z);

        synchronized (lock) {

            int localId = storage.get(index);

            if (localId >= localPalette.size()) {
                return GlobalPalette.getInstance().getState(getAirGlobalId());
            }

            int globalId = localPalette.getInt(localId);
            return GlobalPalette.getInstance().getState(globalId);
        }
    }

    public void setBlock(int x, int y, int z, BlockState state) {
        int globalId = GlobalPalette.getInstance().getStateId(state);
        int index = getIndex(x, y, z);
        int airId = getAirGlobalId();

        synchronized (lock) {
            int oldLocalId = storage.get(index);
            int oldGlobalId = localPalette.getInt(oldLocalId);

            if (globalId == oldGlobalId) return;

            if (oldGlobalId == airId) nonAirBlockCount++;
            if (globalId == airId) nonAirBlockCount--;

            int localIndex = globalToLocal.get(globalId);

            if (localIndex == -1) {
                localIndex = localPalette.size();
                int requiredBits = 32 - Integer.numberOfLeadingZeros(localIndex);

                if (requiredBits > storage.getBitsPerEntry()) {
                    storage = storage.copy(requiredBits);
                }

                localPalette.add(globalId);
                globalToLocal.put(globalId, localIndex);
            }

            storage.set(index, localIndex);
        }
    }

    /**
     * 获取线性索引。
     * 为了支持任意尺寸，必须使用乘法计算。
     * 顺序：Y -> Z -> X (Y为主轴，适合垂直堆叠的结构)
     */
    private int getIndex(int x, int y, int z) {
        //公式：(y * layerArea) + (z * width) + x
        return (y * layerArea) + (z * width) + x;
    }

    public boolean isEmpty() {
        synchronized (lock) {
            return nonAirBlockCount == 0;
        }
    }

    private int getAirGlobalId() {
        if (CACHED_AIR_GLOBAL_ID == -1) {
            SharedBlock airBlock = Registry.getInstance().getBlock(BlockEnums.AIR.getStdRegName());
            if (airBlock == null) throw new IllegalStateException("Air block missing");
            if (!GlobalPalette.getInstance().isBaked()) throw new IllegalStateException("GlobalPalette not baked");
            CACHED_AIR_GLOBAL_ID = GlobalPalette.getInstance().getStateId(airBlock.getDefaultState());
        }
        return CACHED_AIR_GLOBAL_ID;
    }

    public void compact() {
        synchronized (lock) {
            int currentPaletteSize = localPalette.size();
            boolean[] used = new boolean[currentPaletteSize];
            used[0] = true;

            // 使用实例变量 totalSize 进行循环
            for (int i = 0; i < totalSize; i++) {
                int localId = storage.get(i);
                if (localId < currentPaletteSize) {
                    used[localId] = true;
                }
            }

            int newSize = 0;
            for (boolean b : used) if (b) newSize++;

            if (newSize == currentPaletteSize) return;

            int[] oldToNewMap = new int[currentPaletteSize];
            IntArrayList newLocalPalette = new IntArrayList(newSize);
            Int2IntOpenHashMap newGlobalToLocal = new Int2IntOpenHashMap(newSize);
            newGlobalToLocal.defaultReturnValue(-1);

            int nextNewId = 0;
            for (int oldId = 0; oldId < currentPaletteSize; oldId++) {
                if (used[oldId]) {
                    oldToNewMap[oldId] = nextNewId;
                    int globalId = localPalette.getInt(oldId);
                    newLocalPalette.add(globalId);
                    newGlobalToLocal.put(globalId, nextNewId);
                    nextNewId++;
                } else {
                    oldToNewMap[oldId] = -1;
                }
            }

            int requiredBits = 32 - Integer.numberOfLeadingZeros(newSize - 1);
            if (requiredBits < 4) requiredBits = 4;

            // 使用 totalSize 创建新存储
            BitStorage newStorage = new BitStorage(requiredBits, totalSize);
            for (int i = 0; i < totalSize; i++) {
                int oldId = storage.get(i);
                if (oldId >= currentPaletteSize || !used[oldId]) {
                    newStorage.set(i, 0);
                } else {
                    newStorage.set(i, oldToNewMap[oldId]);
                }
            }

            this.localPalette.clear();
            this.localPalette.addAll(newLocalPalette);
            this.globalToLocal.clear();
            this.globalToLocal.putAll(newGlobalToLocal);
            this.storage = newStorage;

            log.debug("Chunk compacted ({}x{}x{}): Palette {}->{}", width, height, depth, currentPaletteSize, newSize);
        }
    }


    public Snapshot createSnapshot() {
        synchronized (lock) {
            //克隆 storage 和 palette
            BitStorage storageCopy = new BitStorage(this.storage);
            IntArrayList paletteCopy = new IntArrayList(this.localPalette);
            return new Snapshot(storageCopy, paletteCopy, width, height, layerArea);
        }
    }

    /**
     * 渲染器专用的快照类。
     * 只读，无锁，线程安全。
     */
    public static class Snapshot {

        @Getter
        private final BitStorage storage;

        @Getter
        private final IntArrayList localPalette;

        @Getter
        private final int width;

        @Getter
        private final int height;

        @Getter
        private final int layerArea;

        public Snapshot(BitStorage storage, IntArrayList localPalette, int width, int height, int layerArea) {
            this.storage = storage;
            this.localPalette = localPalette;
            this.width = width;
            this.height = height;
            this.layerArea = layerArea;
        }

        /**
         * 渲染器调用的高性能 getBlock。
         * 绝对无锁
         */
        public BlockState getBlock(int x, int y, int z) {
            // 内联计算索引，避免方法调用开销
            int index = (y * layerArea) + (z * width) + x;

            // 直接读取，无并发风险
            int localId = storage.get(index);

            // 这里的逻辑和主类一致，但没有任何 synchronized
            if (localId >= localPalette.size()) {
                // 这里需要处理一下，通常快照里的 palette 和 storage 是同步的，
                // 但为了安全起见，如果越界这通常意味着空气
                return GlobalPalette.getInstance().getState(CACHED_AIR_GLOBAL_ID);
            }

            int globalId = localPalette.getInt(localId);
            return GlobalPalette.getInstance().getState(globalId);
        }

        /**
         * 利用 "Local ID 0 始终是 Air" 的约定，避免所有对象创建和字符串比较。
         */
        public boolean isAir(int x, int y, int z) {
            //手动内联索引计算，避免方法调用开销
            int index = (y * layerArea) + (z * width) + x;

            //直接从 BitStorage 获取本地调色板 ID
            int localId = storage.get(index);

            //快速整型比较
            //localId == 0: 构造函数保证了 0 号索引永远是 Air
            //localId >= size: 越界保护，通常意味着未初始化区域，默认为 Air
            return localId == 0 || localId >= localPalette.size();
        }
    }

}