package com.ksptool.ourcraft.server.archive.model;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * CREATE TABLE PUBLIC.PLAYER_INDEX (
 * 	ID BIGINT NOT NULL AUTO_INCREMENT,
 * 	UUID CHARACTER VARYING(128) NOT NULL,
 * 	NAME CHARACTER VARYING(128) NOT NULL,
 * 	POS_X NUMERIC(32,16) NOT NULL,
 * 	POS_Y NUMERIC(32,16) NOT NULL,
 * 	POS_Z NUMERIC(32,16),
 * 	PITCH NUMERIC(32,16) NOT NULL,
 * 	YAW NUMERIC(32,16) NOT NULL,
 * 	HEALTH INTEGER NOT NULL,
 * 	HUNGRY INTEGER NOT NULL,
 * 	"EXP" BIGINT NOT NULL,
 * 	BIN_DATA BINARY LARGE OBJECT,
 * 	CREATE_TIME TIMESTAMP WITH TIME ZONE NOT NULL,
 * 	CONSTRAINT PLAYER_INDEX_PK PRIMARY KEY (ID)
 * );
 */
@Getter@Setter
public class ArchivePlayerVo {

    //主键ID
    private Long id;

    //玩家UUID
    private String uuid;

    //玩家名称
    private String name;

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

    //创建时间
    private LocalDateTime createTime;

}
