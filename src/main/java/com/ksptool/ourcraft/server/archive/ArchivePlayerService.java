package com.ksptool.ourcraft.server.archive;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.apache.commons.lang3.StringUtils;
import com.ksptool.ourcraft.server.archive.model.ArchivePlayerDto;
import com.ksptool.ourcraft.server.archive.model.ArchivePlayerVo;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ArchivePlayerService {

    //归档管理器
    private final ArchiveService archiveService;

    public ArchivePlayerService(ArchiveService archiveService){
        this.archiveService = archiveService;
    }

    /**
     * 保存玩家数据
     * @param dto 玩家DTO
     * @return 保存后的玩家VO，如果保存失败则返回null
     */
    public ArchivePlayerVo savePlayer(ArchivePlayerDto dto){
        if(dto == null){
            log.error("玩家数据不能为空");
            return null;
        }

        var ds = archiveService.getDataSource();
        if(ds == null){
            log.error("归档管理器当前未连接到归档，无法保存玩家数据");
            return null;
        }

        //获取玩家名 并查询归档数据库中是否存在该玩家
        var name = dto.getName();
        var exisisPlayer = loadPlayer(name);

        //如果玩家不存在，则初始化归档中的玩家数据
        if(exisisPlayer == null){

            //数据库插入
            try(Connection conn = ds.getConnection()){
                if(conn == null || conn.isClosed()){
                    log.error("数据库连接异常，无法插入玩家数据");
                    return null;
                }
                
                var sql = "INSERT INTO PLAYER_INDEX (UUID, NAME, WORLD_NAME, LOGIN_COUNT, LAST_LOGIN_TIME, POS_X, POS_Y, POS_Z, YAW, PITCH, HEALTH, HUNGRY, EXP, CREATE_TIME) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try(PreparedStatement stmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)){
                    stmt.setString(1, dto.getUuid());
                    stmt.setString(2, name);
                    stmt.setString(3, dto.getWorldName());
                    stmt.setInt(4, dto.getLoginCount());
                    stmt.setTimestamp(5, Timestamp.valueOf(dto.getLastLoginTime()));
                    stmt.setDouble(6, dto.getPosX());
                    stmt.setDouble(7, dto.getPosY());
                    stmt.setDouble(8, dto.getPosZ());
                    stmt.setDouble(9, dto.getYaw());
                    stmt.setDouble(10, dto.getPitch());
                    stmt.setInt(11, dto.getHealth());
                    stmt.setInt(12, dto.getHungry());
                    stmt.setLong(13, dto.getExp());
                    stmt.setTimestamp(14, Timestamp.valueOf(LocalDateTime.now()));
                    stmt.executeUpdate();
                    
                    var updateVo = new ArchivePlayerVo();
                    try(ResultSet rs = stmt.getGeneratedKeys()){
                        if(rs.next()){
                            updateVo.setId(rs.getLong(1));
                        }
                    }
                    updateVo.setUuid(dto.getUuid());
                    updateVo.setName(name);
                    updateVo.setWorldName(dto.getWorldName());
                    updateVo.setLoginCount(dto.getLoginCount());
                    updateVo.setLastLoginTime(dto.getLastLoginTime());
                    updateVo.setPosX(dto.getPosX());
                    updateVo.setPosY(dto.getPosY());
                    updateVo.setPosZ(dto.getPosZ());
                    updateVo.setYaw(dto.getYaw());
                    updateVo.setPitch(dto.getPitch());
                    updateVo.setHealth(dto.getHealth());
                    updateVo.setHungry(dto.getHungry());
                    updateVo.setExp(dto.getExp());
                    updateVo.setCreateTime(LocalDateTime.now());
                    
                    log.info("为玩家 {} 创建新的归档记录 UUID {}", name, dto.getUuid());
                    return updateVo;
                }
            }catch(SQLException e){
                log.error("插入玩家数据失败", e);
                return null;
            }

        }

        //如果玩家存在，则更新归档中的玩家数据
        var updateVo = new ArchivePlayerVo();
        updateVo.setId(exisisPlayer.getId());
        updateVo.setUuid(exisisPlayer.getUuid());
        updateVo.setName(exisisPlayer.getName());
        updateVo.setWorldName(dto.getWorldName());
        updateVo.setLoginCount(dto.getLoginCount());
        updateVo.setLastLoginTime(dto.getLastLoginTime());
        updateVo.setPosX(dto.getPosX());
        updateVo.setPosY(dto.getPosY());
        updateVo.setPosZ(dto.getPosZ());
        updateVo.setYaw(dto.getYaw());
        updateVo.setPitch(dto.getPitch());
        updateVo.setHealth(dto.getHealth());
        updateVo.setHungry(dto.getHungry());
        updateVo.setExp(dto.getExp());
        
        //数据库更新
        try(Connection conn = ds.getConnection()){

            if(conn == null || conn.isClosed()){
                log.error("数据库连接异常，无法更新玩家数据");
                return null;
            }

            var sql = "UPDATE PLAYER_INDEX SET UUID = ?, NAME = ?, WORLD_NAME = ?, LOGIN_COUNT = ?, LAST_LOGIN_TIME = ?, POS_X = ?, POS_Y = ?, POS_Z = ?, YAW = ?, PITCH = ?, HEALTH = ?, HUNGRY = ?, EXP = ? WHERE ID = ?";

            try(PreparedStatement stmt = conn.prepareStatement(sql)){
                stmt.setString(1, updateVo.getUuid());
                stmt.setString(2, updateVo.getName());
                stmt.setString(3, updateVo.getWorldName());
                stmt.setInt(4, updateVo.getLoginCount());
                stmt.setTimestamp(5, Timestamp.valueOf(updateVo.getLastLoginTime()));
                stmt.setDouble(6, updateVo.getPosX());
                stmt.setDouble(7, updateVo.getPosY());
                stmt.setDouble(8, updateVo.getPosZ());
                stmt.setDouble(9, updateVo.getYaw());
                stmt.setDouble(10, updateVo.getPitch());
                stmt.setInt(11, updateVo.getHealth());
                stmt.setInt(12, updateVo.getHungry());
                stmt.setLong(13, updateVo.getExp());
                stmt.setLong(14, updateVo.getId());
                stmt.executeUpdate();
                log.info("为玩家 {} 更新归档记录 UUID {}", name, updateVo.getUuid());
            }
            
            return updateVo;
            
        }catch(SQLException e){
            log.error("更新玩家数据失败", e);
            return null;
        }
        
    }

    /**
     * 加载玩家数据
     * @param playerName 玩家名称
     */
    public ArchivePlayerVo loadPlayer(String playerName){

        if(StringUtils.isBlank(playerName)){
            log.error("玩家名称不能为空");
            return null;
        }
        
        var ds = archiveService.getDataSource();

        if(ds == null){
            log.error("归档管理器当前未连接到归档，无法加载玩家数据");
            return null;
        }

        //查询是否有玩家数据
        try(Connection conn = ds.getConnection()){
            if(conn == null || conn.isClosed()){
                log.error("数据库连接异常，无法加载玩家数据");
                return null;
            }

            var sql = "SELECT * FROM PLAYER_INDEX WHERE NAME = ?";

            try(PreparedStatement stmt = conn.prepareStatement(sql)){
                stmt.setString(1, playerName);
                try(ResultSet rs = stmt.executeQuery()){

                    if(!rs.next()){
                        return null;
                    }

                    var vo = new ArchivePlayerVo();
                    vo.setId(rs.getLong("ID"));
                    vo.setUuid(rs.getString("UUID"));
                    vo.setName(rs.getString("NAME"));
                    vo.setWorldName(rs.getString("WORLD_NAME"));
                    vo.setLoginCount(rs.getInt("LOGIN_COUNT"));
                    var lastLoginTimestamp = rs.getTimestamp("LAST_LOGIN_TIME");
                    if(lastLoginTimestamp != null){
                        vo.setLastLoginTime(lastLoginTimestamp.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
                    }
                    vo.setPosX(rs.getDouble("POS_X"));
                    vo.setPosY(rs.getDouble("POS_Y"));
                    vo.setPosZ(rs.getDouble("POS_Z"));
                    vo.setYaw(rs.getDouble("YAW"));
                    vo.setPitch(rs.getDouble("PITCH"));
                    vo.setHealth(rs.getInt("HEALTH"));
                    vo.setHungry(rs.getInt("HUNGRY"));
                    vo.setExp(rs.getLong("EXP"));
                    var createTimestamp = rs.getTimestamp("CREATE_TIME");
                    if(createTimestamp != null){
                        vo.setCreateTime(createTimestamp.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
                    }
                    return vo;
                }
            }

        }catch(SQLException e){
            log.error("加载玩家数据失败", e);
            return null;   
        }

    }
}
