package com.ksptool.ourcraft.sharedcore.network.nvo;

/**
 * 服务端响应客户端认证结果数据包
 * 认证成功时，会携带sessionId
 * 认证失败时，会携带拒绝原因
 * accepted: 0:接受, 1:拒绝
 * 
 * 这是一个RPC响应数据包,必须通过Rpc方式进行传输
 */
public record AuthRpcVo(int accepted, long sessionId, String reason) {

    /**
     * 接受认证
     * @param sessionId 会话ID
     * @return 认证结果数据包
     */
    public static AuthRpcVo accept(long sessionId) {
        return new AuthRpcVo(0, sessionId, null);
    }

    /**
     * 拒绝认证
     * @param reason 拒绝原因
     * @return 认证结果数据包
     */
    public static AuthRpcVo reject(String reason) {
        return new AuthRpcVo(1, -1, reason);
    }

}
