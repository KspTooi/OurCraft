package com.ksptool.ourcraft.sharedcore.network.packets;

/**
 * 服务端发送给客户端实体位置和朝向(含俯仰与偏航) (Server Sync Entity Position And Rotation Network View Object)
 */
public record ServerSyncEntityPositionAndRotationNVo(int entityId, double x, double y, double z, float yaw, float pitch) {}

