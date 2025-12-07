package com.ksptool.ourcraft.sharedcore.network;

/**
 * 远程过程调用请求数据包
 * requestId: 请求ID
 * data: 请求数据
 */
public record RpcRequest<T>(long requestId, T data) {
    public static <T> RpcRequest<T> of(long requestId, T data) {
        return new RpcRequest<>(requestId, data);
    }
}
