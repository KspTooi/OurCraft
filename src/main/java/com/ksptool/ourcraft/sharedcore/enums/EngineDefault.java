package com.ksptool.ourcraft.sharedcore.enums;

public class EngineDefault {

    //引擎版本
    public static String ENGINE_VERSION = "1.2F3";

    //SCA封装大小(这决定了一个SCA文件将封装多少个区块，通常为40即40x40个区块 警告: 如果修改了这个值则旧的SCA文件将无法加载)
    public static int SCA_PACKAGE_SIZE = 40;

    //默认世界名称
    public static String DEFAULT_WORLD_NAME = "earth_like";

    //默认世界模板(标准注册名)
    public static String DEFAULT_WORLD_TEMPLATE = "ourcraft:earth_like";

    //最大服务端世界执行单元线程数(-1表示不限制)
    public static int MAX_SWEU_THREAD_COUNT = -1;

    //最大服务端世界执行单元队列大小(-1表示不限制)
    public static int MAX_SWEU_QUEUE_SIZE = -1;

    //最大区块处理线程数(-1表示不限制)
    public static int MAX_CHUNK_PROCESS_THREAD_COUNT = 2;

    //最大区块处理队列大小(-1表示不限制)
    public static int MAX_CHUNK_PROCESS_QUEUE_SIZE = 5000;



    public static int getMaxSWEUThreadCount() {
        return MAX_SWEU_THREAD_COUNT == -1 ? Integer.MAX_VALUE : MAX_SWEU_THREAD_COUNT;
    }

    public static int getMaxSWEUQueueSize() {
        return MAX_SWEU_QUEUE_SIZE == -1 ? Integer.MAX_VALUE : MAX_SWEU_QUEUE_SIZE;
    }

    public static int getMaxChunkProcessThreadCount() {
        return MAX_CHUNK_PROCESS_THREAD_COUNT == -1 ? Integer.MAX_VALUE : MAX_CHUNK_PROCESS_THREAD_COUNT;
    }

    public static int getMaxChunkProcessQueueSize() {
        return MAX_CHUNK_PROCESS_QUEUE_SIZE == -1 ? Integer.MAX_VALUE : MAX_CHUNK_PROCESS_QUEUE_SIZE;
    }


}
