package com.ksptool.ourcraft.sharedcore.network.ndto;

/**
 * 客户端请求认证数据包
 * 这是一个RPC请求数据包,必须通过Rpc方式进行传输
 */
public record AuthRpcDto(String playerName, String clientVersion) {

    public static AuthRpcDto of(String playerName, String clientVersion) {
        return new AuthRpcDto(playerName, clientVersion);
    }

}
