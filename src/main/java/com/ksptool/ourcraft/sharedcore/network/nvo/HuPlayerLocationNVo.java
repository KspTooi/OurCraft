package com.ksptool.ourcraft.sharedcore.network.nvo;

/**
 * 服务端反馈玩家位置(Hot Update Player Location Adjust Result Network View Object)
 */
public record HuPlayerLocationNVo (double x, double y, double z, float yaw, float pitch) {

    public static HuPlayerLocationNVo of(double x, double y, double z, float yaw, float pitch) {
        return new HuPlayerLocationNVo(x, y, z, yaw, pitch);
    }
}
