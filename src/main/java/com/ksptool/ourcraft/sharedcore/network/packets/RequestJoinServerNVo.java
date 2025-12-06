package com.ksptool.ourcraft.sharedcore.network.packets;

/**
 * 服务器响应客户端加入服务器 (Server Response Client Join Server Network View Object)
 * accepted: 0=拒绝, 1=接受
 * 当accepted==1时，会携带sessionId和初始位置信息
 */
@Deprecated
public record RequestJoinServerNVo(
        int accepted,  // 0=拒绝, 1=接受
        String reason, // 拒绝原因
        Long sessionId, // 玩家本次在世界中的唯一ID
        Double x, // 玩家出生点X
        Double y, // 玩家出生点Y
        Double z, // 玩家出生点Z
        Float yaw, // 玩家朝向Yaw
        Float pitch)  // 玩家朝向Pitch
{

        public static RequestJoinServerNVo accept(Long sessionId, Double x, Double y, Double z, Float yaw, Float pitch) {
            return new RequestJoinServerNVo(1, null, sessionId, x, y, z, yaw, pitch);
        }

        public static RequestJoinServerNVo reject(String reason) {
            return new RequestJoinServerNVo(0, reason, null, null, null, null, null, null);
        }

}



