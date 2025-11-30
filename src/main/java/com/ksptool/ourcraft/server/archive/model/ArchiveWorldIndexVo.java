package com.ksptool.ourcraft.server.archive.model;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

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

    //默认玩家出生点X
    private Integer defaultSpawnX;

    //默认玩家出生点Y
    private Integer defaultSpawnY;

    //默认玩家出生点Z
    private Integer defaultSpawnZ;

    //默认玩家出生点是否已被创建/初始化（0=否, 1=是）
    private Integer defaultSpawnCreated;

    //创建时间
    private LocalDateTime createTime;
}
