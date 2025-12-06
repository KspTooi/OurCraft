package com.ksptool.ourcraft.sharedcore.network.nvo;

/**
 * 服务端响应客户端认证结果数据包
 * 认证成功时，会携带sessionId
 * 认证失败时，会携带拒绝原因
 * accepted: 0:接受, 1:拒绝
 */
public record AuthNVo(int accepted, long sessionId, String reason) {

    /**
     * 接受认证
     * @param sessionId 会话ID
     * @return 认证结果数据包
     */
    public static AuthNVo accept(long sessionId) {
        return new AuthNVo(0, sessionId, null);
    }

    /**
     * 拒绝认证
     * @param reason 拒绝原因
     * @return 认证结果数据包
     */
    public static AuthNVo reject(String reason) {
        return new AuthNVo(1, -1, reason);
    }

}
