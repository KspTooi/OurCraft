package com.ksptool.ourcraft.sharedcore.network.nvo;

public record PsPlayerNVo(
    String uuid, //玩家UUID
    String name, //玩家名称
    int health, //玩家血量
    int hungry, //玩家饥饿度
    double posX, //玩家位置X
    double posY, //玩家位置Y
    double posZ, //玩家位置Z
    double yaw, //玩家朝向Yaw
    double pitch, //玩家朝向Pitch
    float ga, //地面加速度
    float aa, //空中加速度
    float ms //最大移动速度
) {

    public static PsPlayerNVo of(String uuid, String name, int health, int hungry, double posX, double posY, double posZ, double yaw, double pitch, float ga, float aa, float ms) {
        return new PsPlayerNVo(uuid, name, health, hungry, posX, posY, posZ, yaw, pitch, ga, aa, ms);
    }

}
