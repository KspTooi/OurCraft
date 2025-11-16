package com.ksptool.ourcraft.sharedcore.network.packets;

/**
 * 服务器响应状态 (Get Server Status Network View Object)
 */
public record GetServerStatusNVo(String serverVersion, String serverName, int maxPlayers, int onlinePlayers, String serverStatus) {}

