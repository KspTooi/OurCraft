package com.ksptool.ourcraft.sharedcore.network.packets;

/**
 * 当世界中某个方块发生变化时，同步该变化给客户端 (Server Sync Block Update Network View Object)
 */
public record ServerSyncBlockUpdateNVo(int x, int y, int z, int blockId) {}

