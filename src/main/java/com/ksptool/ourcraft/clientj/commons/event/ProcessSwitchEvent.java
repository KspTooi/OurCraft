package com.ksptool.ourcraft.clientj.commons.event;

import java.time.LocalDateTime;

import com.ksptool.ourcraft.clientj.commons.ClientEvent;
import com.ksptool.ourcraft.clientj.network.ClientNetworkSession;
import com.ksptool.ourcraft.sharedcore.network.nvo.PsNVo;

import lombok.Getter;

/**
 * 当服务器要求客户端切换进程时触发的事件
 */
@Getter
public class ProcessSwitchEvent implements ClientEvent{

    private final String worldName;           //目标世界的名称(标准注册名)
    private final String worldTemplate;       //目标世界的模板标准注册名
    private final int aps;                    //目标世界的APS
    private final long totalActions;          //目标世界的总Action数
    private final LocalDateTime startDateTime; //目标世界的开始时间
    private final ClientNetworkSession session; //网络会话

    public ProcessSwitchEvent(ClientNetworkSession session,String worldName, String worldTemplate, int aps, long totalActions, LocalDateTime startDateTime) {
        this.worldName = worldName;
        this.worldTemplate = worldTemplate;
        this.aps = aps;
        this.totalActions = totalActions;
        this.startDateTime = startDateTime;
        this.session = session;
    }

    public static ProcessSwitchEvent of(ClientNetworkSession session,String worldName, String worldTemplate, int aps, long totalActions, LocalDateTime startDateTime) {
        return new ProcessSwitchEvent(session, worldName, worldTemplate, aps, totalActions, startDateTime);
    }

    public static ProcessSwitchEvent of(ClientNetworkSession session,PsNVo vo) {
        return new ProcessSwitchEvent(session, vo.worldName(), vo.worldTemplate(), vo.aps(), vo.totalActions(), vo.startDateTime());
    }

}
