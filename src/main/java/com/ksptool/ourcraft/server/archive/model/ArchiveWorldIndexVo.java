package com.ksptool.ourcraft.server.archive.model;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 用于表示一个归档中的世界索引数据
 * CREATE TABLE PUBLIC.WORLD_INDEX (
 * 	ID BIGINT NOT NULL AUTO_INCREMENT,
 * 	NAME CHARACTER VARYING(128) NOT NULL,
 * 	SEED CHARACTER VARYING(512) NOT NULL,
 * 	TOTAL_TICK BIGINT NOT NULL,
 * 	TEMPLATE_STD_REG_NAME CHARACTER VARYING(512) NOT NULL,
 * 	SPAWN_X INTEGER NOT NULL,
 * 	SPAWN_Y INTEGER,
 * 	SPAWN_Z INTEGER,
 * 	CONSTRAINT WORLD_INDEX_PK PRIMARY KEY (ID)
 * );
 */
@Getter@Setter
public class ArchiveWorldIndexVo {

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

    //生成时间(世界生成时间)
    private LocalDateTime createTime;

}
