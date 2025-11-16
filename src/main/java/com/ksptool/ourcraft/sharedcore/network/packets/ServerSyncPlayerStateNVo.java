package com.ksptool.ourcraft.sharedcore.network.packets;

/**
 * 服务端发送给客户端玩家状态 (Server Sync Player State Network View Object)
 */
public record ServerSyncPlayerStateNVo(float health, int foodLevel, int experienceLevel, float experienceProgress) {}

