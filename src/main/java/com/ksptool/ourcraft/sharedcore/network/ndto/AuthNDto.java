package com.ksptool.ourcraft.sharedcore.network.ndto;

/**
 * 客户端请求认证数据包
 */
public record AuthNDto(String playerName, String clientVersion) {

    public static AuthNDto of(String playerName, String clientVersion) {
        return new AuthNDto(playerName, clientVersion);
    }

}
