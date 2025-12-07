package com.ksptool.ourcraft.sharedcore.network;


/**
 * 远程过程调用响应数据包
 * requestId: 请求ID(与请求数据包的requestId相同)
 * data: 响应数据
 */
public record RpcResponse<T>(long requestId, T data) {
    public static <T> RpcResponse<T> of(long requestId, T data) {
        return new RpcResponse<>(requestId, data);
    }
}
