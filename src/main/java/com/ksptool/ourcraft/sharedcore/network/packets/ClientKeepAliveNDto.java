package com.ksptool.ourcraft.sharedcore.network.packets;

/**
 * 客户端发送给服务端的心跳包，以维持连接 (Client Keep Alive Network Package)
 */
public record ClientKeepAliveNDto(long timestamp) {}

