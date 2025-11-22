package com.ksptool.ourcraft.server.archive.model;

import java.time.LocalDateTime;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import lombok.Getter;
import lombok.Setter;

@Getter@Setter
public class ArchivePlayerDto {

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

    /**
     * 验证玩家数据
     * @return 验证结果
     */
    private String validate(){

        if(StringUtils.isBlank(uuid)){
            return "玩家UUID不能为空";
        }

        if(StringUtils.isBlank(name)){
            return "玩家名称不能为空";
        }

        if(posX == null){
            return "玩家位置X不能为空";
        }

        if(posY == null){
            return "玩家位置Y不能为空";
        }
        
        if(posZ == null){
            return "玩家位置Z不能为空";
        }

        if(yaw == null){
            return "玩家朝向Yaw不能为空";
        }
        
        if(pitch == null){
            return "玩家朝向Pitch不能为空";
        }

        if(health == null){
            return "玩家血量不能为空";
        }
        
        if(hungry == null){
            return "玩家饥饿度不能为空";
        }
        
        if(exp == null){
            return "玩家经验不能为空";
        }

        if(createTime == null){
            return "玩家创建时间不能为空";
        }

        return null;
    }

}
