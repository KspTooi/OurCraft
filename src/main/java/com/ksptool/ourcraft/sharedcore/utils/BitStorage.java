package com.ksptool.ourcraft.sharedcore.utils;

import lombok.Getter;

public class BitStorage {

    private final long[] data;

    @Getter
    private final int bitsPerEntry;
    private final int size;
    private final long maxEntryValue;

    public BitStorage(int bitsPerEntry, int size) {
        this.bitsPerEntry = bitsPerEntry;
        this.size = size;
        this.maxEntryValue = (bitsPerEntry == 64) ? -1L : (1L << bitsPerEntry) - 1;
        int arraySize = (int) (((long) size * bitsPerEntry + 63) / 64);
        this.data = new long[arraySize];
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

    // 用于扩容时的数据迁移构造
    private BitStorage(int bitsPerEntry, int size, long[] data) {
        this.bitsPerEntry = bitsPerEntry;
        this.size = size;
        this.maxEntryValue = (bitsPerEntry == 64) ? -1L : (1L << bitsPerEntry) - 1;
        this.data = data;
    }

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

    public BitStorage copy(int newBitsPerEntry) {
        BitStorage newStorage = new BitStorage(newBitsPerEntry, size);
        for (int i = 0; i < size; i++) {
            newStorage.set(i, this.get(i));
        }
        return newStorage;
    }
}