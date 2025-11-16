package com.ksptool.ourcraft.sharedcore.network.packets;

/**
 * 服务器响应客户端加入服务器 (Server Response Client Join Server Network View Object)
 * accepted: 0=拒绝, 1=接受
 * 当accepted==1时，会携带sessionId和初始位置信息
 */
public record RequestJoinServerNVo(int accepted, String reason, Integer sessionId, Double x, Double y, Double z, Float yaw, Float pitch) {}

