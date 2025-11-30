package com.ksptool.ourcraft.server.archive.model;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter@Setter
public class ArchivePlayerDto {

    //玩家UUID
    private String uuid;

    //玩家名称
    private String name;

    //登录次数
    private Integer loginCount;

    //最后登录时间
    private LocalDateTime lastLoginTime;

    //玩家所在世界
    private String worldName;

    //玩家位置X
    private Double posX;

    //玩家位置Y
    private Double posY;

    //玩家位置Z
    private Double posZ;

    //玩家朝向Yaw
    private Double yaw;

    //玩家朝向Pitch
    private Double pitch;

    //玩家血量
    private Integer health;

    //玩家饥饿度
    private Integer hungry;

    //玩家经验
    private Long exp;
}
