package com.ksptool.ourcraft.server.archive.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 用于表示一个归档中的世界索引数据
 * CREATE TABLE PUBLIC.WORLD_INDEX (
 * 	ID BIGINT NOT NULL AUTO_INCREMENT,
 * 	NAME CHARACTER VARYING(128) NOT NULL,
 * 	SEED CHARACTER VARYING(512) NOT NULL,
 * 	TOTAL_TICK BIGINT NOT NULL,
 * 	TEMPLATE_STD_REG_NAME CHARACTER VARYING(512) NOT NULL,
 * 	SPAWN_X INTEGER NOT NULL,
 * 	SPAWN_Y INTEGER NOT NULL,
 * 	SPAWN_Z INTEGER NOT NULL,
 *  SPAWN_CREATED INTEGER NOT NULL,
 *  CREATE_TIME TIMESTAMP WITH TIME ZONE NOT NULL,
 * 	CONSTRAINT WORLD_INDEX_PK PRIMARY KEY (ID)
 * );
 * COMMENT ON TABLE PUBLIC.WORLD_INDEX IS '世界索引表，存储所有已创建世界的基本信息';
 * COMMENT ON COLUMN PUBLIC.WORLD_INDEX.ID IS '世界记录的唯一标识符（主键）';
 * COMMENT ON COLUMN PUBLIC.WORLD_INDEX.NAME IS '世界唯一标识符(世界名称)';
 * COMMENT ON COLUMN PUBLIC.WORLD_INDEX.SEED IS '世界生成的随机种子';
 * COMMENT ON COLUMN PUBLIC.WORLD_INDEX.TOTAL_TICK IS '世界运行的总Action数';
 * COMMENT ON COLUMN PUBLIC.WORLD_INDEX.TEMPLATE_STD_REG_NAME IS '世界模板标准注册名';
 * COMMENT ON COLUMN PUBLIC.WORLD_INDEX.SPAWN_X IS '玩家默认出生点的 X 坐标';
 * COMMENT ON COLUMN PUBLIC.WORLD_INDEX.SPAWN_Y IS '玩家默认出生点的 Y 坐标';
 * COMMENT ON COLUMN PUBLIC.WORLD_INDEX.SPAWN_Z IS '玩家默认出生点的 Z 坐标';
 * COMMENT ON COLUMN PUBLIC.WORLD_INDEX.SPAWN_CREATED IS '玩家默认出生点是否已被创建/初始化（0=否, 1=是）';
 * COMMENT ON COLUMN PUBLIC.WORLD_INDEX.CREATE_TIME IS '世界创建时间';
 */
@Getter@Setter
public class ArchiveWorldIndexDto {

    //主键ID
    private Long id;

    //世界名称
    private String name;

    //世界种子
    private String seed;
    
    //总Tick(该世界自开始运行以来的总Tick数)
    private Long totalTick;

    //世界模板(世界模板标准注册名称)
    private String templateStdRegName;

    //出生点X
    private Integer spawnX;

    //出生点Y
    private Integer spawnY;
    
    //出生点Z
    private Integer spawnZ;

    //默认玩家出生点是否已被创建/初始化（0=否, 1=是）
    private Integer defaultSpawnCreated;

}
