package com.ksptool.ourcraft.sharedcore.network.nvo;

public record HuPlayerLocationNVo (double x, double y, double z, float yaw, float pitch) {

    public static HuPlayerLocationNVo of(double x, double y, double z, float yaw, float pitch) {
        return new HuPlayerLocationNVo(x, y, z, yaw, pitch);
    }


}
