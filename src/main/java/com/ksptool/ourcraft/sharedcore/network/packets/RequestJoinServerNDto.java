package com.ksptool.ourcraft.sharedcore.network.packets;

/**
 * 客户端请求加入服务器 (Request Join Server Network Data Transfer Object)
 */
public record RequestJoinServerNDto(String clientVersion, String playerName) {}

