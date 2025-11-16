package com.ksptool.ourcraft.sharedcore.network.packets;

/**
 * 玩家输入状态数据包 (Player Input State Network Data Transfer Object)
 * 用于客户端向服务器发送玩家的输入状态，支持客户端预测和服务端回滚
 */
public record PlayerInputStateNDto(
    int clientTick,      // 客户端的时间戳，用于服务器"回滚"
    boolean w,           // W 键是否按下
    boolean s,           // S 键是否按下
    boolean a,           // A 键是否按下
    boolean d,           // D 键是否按下
    boolean space,       // 空格键是否按下
    boolean shift,       // Shift 键是否按下
    float yaw,           // 鼠标当前的 Yaw
    float pitch          // 鼠标当前的 Pitch
) {}

