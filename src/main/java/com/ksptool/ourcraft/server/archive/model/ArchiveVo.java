package com.ksptool.ourcraft.server.archive.model;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Setter;

/**
 * 用于表示一个归档的视图对象
 * 
 * 归档文件结构:
 * SAVES
 * --[归档名称]
 *   |--archive.index    ----> (存储该归档中所有可用的世界)
 *   |--players        ----> (存储该归档中的玩家信息)
 *      |--[玩家UUID].index ----> (存储玩家的位置、物品栏等数据)
 *   |--[世界名]_chunk  ----> (存储该世界中的蛆块数据)
 *   |--[世界名]_entity ----> (存储该世界中的实体数据)
 */
@Setter
public class ArchiveVo {

    //归档名称
    private String name;

    //版本
    private String version;

    //创建时间
    private LocalDateTime createTime;

    //最后修改时间
    private LocalDateTime updateTime;

}
