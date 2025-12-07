package com.ksptool.ourcraft.sharedcore.network.nvo;
import java.time.LocalDateTime;

/**
 * 服务端通知客户端需要进程切换(Process Switch Network View Object)
 */
public record PsNVo(
    String worldName,           //目标世界的名称(标准注册名)
    String worldTemplate,       //目标世界的模板标准注册名
    int aps,                    //目标世界的APS
    long totalActions,          //目标世界的总Action数
    LocalDateTime startDateTime //目标世界的开始时间
) {

    public static PsNVo of(String worldName, String worldTemplate, int aps, long totalActions, LocalDateTime startDateTime) {
        return new PsNVo(worldName, worldTemplate, aps, totalActions, startDateTime);
    }

}
