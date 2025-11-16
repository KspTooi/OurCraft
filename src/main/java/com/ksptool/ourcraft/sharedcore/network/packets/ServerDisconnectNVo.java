package com.ksptool.ourcraft.sharedcore.network.packets;

/**
 * 当服务端需要踢出玩家时发送此NVo (Server Disconnect Network View Object)
 */
public record ServerDisconnectNVo(String reason) {}

