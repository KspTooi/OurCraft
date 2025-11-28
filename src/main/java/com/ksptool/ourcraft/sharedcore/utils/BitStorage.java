package com.ksptool.ourcraft.sharedcore.utils;

import lombok.Getter;

public class BitStorage {

    @Getter
    //块数据
    private final long[] data;

    @Getter
    //每个块需要多少位来存储
    private final int bitsPerEntry; 

    //总块大小
    private final int size;

    //最大块值(用于位运算)
    private final long maxEntryValue;

    /**
     * 构造函数
     * @param bitsPerEntry 每个块需要多少位来存储
     * @param size 总块数
     */
    public BitStorage(int bitsPerEntry, int size) {
        this.bitsPerEntry = bitsPerEntry;
        this.size = size;
        this.maxEntryValue = (bitsPerEntry == 64) ? -1L : (1L << bitsPerEntry) - 1;
        int arraySize = (int) (((long) size * bitsPerEntry + 63) / 64);
        this.data = new long[arraySize];
    }

    /**
     * 构造函数
     * @param x 区块X尺寸
     * @param y 区块Y尺寸
     * @param z 区块Z尺寸
     * @param bitsPerEntry 每个块需要多少位来存储
     * @param data 数据数组
     */
    public BitStorage(int x, int y, int z,int bitsPerEntry, long[] data) {
        this.bitsPerEntry = bitsPerEntry; //每个块需要多少位来存储
        this.size = x * y * z; //总块数
        this.maxEntryValue = (bitsPerEntry == 64) ? -1L : (1L << bitsPerEntry) - 1; //最大值
        this.data = data; //数据数组
    }

    /**
     * 高性能的拷贝构造函数 用于实现COW
     * 直接克隆底层数组
     */
    public BitStorage(BitStorage other) {
        this.bitsPerEntry = other.bitsPerEntry;
        this.size = other.size;
        this.maxEntryValue = other.maxEntryValue;
        this.data = other.data.clone();
    }

    /**
     * 设置指定索引处的值
     * @param index 索引
     * @param value 值
     */
    public void set(int index, int value) {
        // 移除边界检查以提升性能（调用端保证安全）
        long bitIndex = (long) index * bitsPerEntry;
        int longIndex = (int) (bitIndex >> 6);
        int bitOffset = (int) (bitIndex & 63);

        long currentLong = data[longIndex];
        currentLong &= ~(maxEntryValue << bitOffset);
        currentLong |= ((long) value & maxEntryValue) << bitOffset;
        data[longIndex] = currentLong;

        int bitsWritten = 64 - bitOffset;
        if (bitsWritten < bitsPerEntry) {
            int longIndexNext = longIndex + 1;
            long nextLong = data[longIndexNext];
            int bitsLeft = bitsPerEntry - bitsWritten;
            nextLong &= -(1L << bitsLeft);
            nextLong |= ((long) value & maxEntryValue) >> bitsWritten;
            data[longIndexNext] = nextLong;
        }
    }

    /**
     * 获取指定索引处的值
     * @param index 索引
     * @return 值
     */
    public int get(int index) {
        long bitIndex = (long) index * bitsPerEntry;
        int longIndex = (int) (bitIndex >> 6);
        int bitOffset = (int) (bitIndex & 63);

        long currentLong = data[longIndex];
        long value = (currentLong >>> bitOffset);

        int bitsRead = 64 - bitOffset;
        if (bitsRead < bitsPerEntry) {
            long nextLong = data[longIndex + 1];
            value |= (nextLong << bitsRead);
        }
        return (int) (value & maxEntryValue);
    }

    /**
     * 拷贝构造函数
     * @param newBitsPerEntry 新的存储位数
     * @return 新的BitStorage
     */
    public BitStorage copy(int newBitsPerEntry) {
        BitStorage newStorage = new BitStorage(newBitsPerEntry, size);
        for (int i = 0; i < size; i++) {
            newStorage.set(i, this.get(i));
        }
        return newStorage;
    }
}