package com.ksptool.ourcraft.sharedcore.network.nvo;

/**
 * 服务端发送给客户端的配置批数据包
 * kind: 数据类型
 * data: 数据内容
 */
public record BatchDataNVo(int kind, byte[] data) {

    public static BatchDataNVo of(int kind, byte[] data) {
        return new BatchDataNVo(kind, data);
    }
    
}
