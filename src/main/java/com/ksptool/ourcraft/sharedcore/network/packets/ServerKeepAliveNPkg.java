package com.ksptool.ourcraft.sharedcore.network.packets;

/**
 * 服务端发送给客户端的心跳包，以维持连接 (Server Keep Alive Network Package)
 */
public record ServerKeepAliveNPkg(long timestamp) {}

