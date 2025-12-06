package com.ksptool.ourcraft.sharedcore.network.packets;

/**
 * 玩家声明更改位置和摄像机旋转角度(含俯仰与偏航) (Player Declare Change Position And Rotation Network Data Transfer Object)
 */
@Deprecated
public record PlayerDcparNDto(double x, double y, double z, float yaw, float pitch) {}

