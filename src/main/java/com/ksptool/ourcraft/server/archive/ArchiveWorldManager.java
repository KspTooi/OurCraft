package com.ksptool.ourcraft.server.archive;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.ksptool.ourcraft.server.archive.model.ArchiveWorldIndexDto;
import org.apache.commons.lang3.StringUtils;
import com.ksptool.ourcraft.server.archive.model.ArchiveWorldIndexVo;
import com.ksptool.ourcraft.server.world.ServerWorld;
import com.ksptool.ourcraft.sharedcore.GlobalPalette;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ArchiveWorldManager {

    //归档管理器
    private final ArchiveManager archiveManager;

    //归档调色板管理器
    private final ArchivePaletteManager paletteManager;

    //归档区块管理器
    private final ArchiveChunkManager chunkManager;

    public ArchiveWorldManager(ArchiveManager archiveManager, ArchivePaletteManager paletteManager, ArchiveChunkManager chunkManager){
        this.archiveManager = archiveManager;
        this.paletteManager = paletteManager;
        this.chunkManager = chunkManager;
    }

    public void saveWorld(ServerWorld world){

        if(world == null){
            log.error("世界不能为空");
            return;
        }

        var archiveName = archiveManager.getCurrentArchiveName();
        
        if(StringUtils.isBlank(archiveName)){
            log.error("当前未连接到归档，无法保存世界数据");
            return;
        }

        //直接保存世界运行时到归档索引(归档索引不存在时自动创建)
        var dto = new ArchiveWorldIndexDto();
        dto.setName(world.getWorldName());
        dto.setSeed(world.getSeed()+"");
        dto.setTotalTick(world.getGameTime()); //@待完善 GameTime不一定为总Tick
        dto.setTemplateStdRegName(world.getTemplate().getTemplateId());
        saveWorldIndex(dto);
        
        //保存当前的全局调色板数据
        paletteManager.saveGlobalPalette(GlobalPalette.getInstance());

        
    }



    /**
     * 保存世界索引数据(不存在时自动创建存在则更新)
     * @param dto 世界索引数据
     */
    public void saveWorldIndex(ArchiveWorldIndexDto dto){

        if(dto == null){
            log.error("世界索引数据不能为空");
            return;
        }

        var ds = archiveManager.getDataSource();
        if(ds == null){
            log.error("归档管理器当前未连接到归档，无法保存世界索引数据");
            return;
        }

        if(StringUtils.isBlank(dto.getName())){
            log.error("世界名称不能为空");
            return;
        }

        var existWorldIndex = loadWorldIndex(dto.getName());

        if(existWorldIndex == null){
            try(Connection conn = ds.getConnection()){
                if(conn == null || conn.isClosed()){
                    log.error("数据库连接异常，无法插入世界索引数据");
                    return;
                }

                var sql = "INSERT INTO WORLD_INDEX (NAME, SEED, TOTAL_TICK, TEMPLATE_STD_REG_NAME, SPAWN_X, SPAWN_Y, SPAWN_Z) VALUES (?, ?, ?, ?, ?, ?, ?)";
                try(PreparedStatement stmt = conn.prepareStatement(sql)){
                    stmt.setString(1, dto.getName());
                    stmt.setString(2, dto.getSeed());
                    stmt.setLong(3, dto.getTotalTick());
                    stmt.setString(4, dto.getTemplateStdRegName());
                    stmt.setInt(5, 0);
                    stmt.setInt(6, 0);
                    stmt.setInt(7, 0);
                    stmt.executeUpdate();
                    log.info("为世界 {} 创建新的归档记录", dto.getName());
                }
            }catch(SQLException e){
                log.error("插入世界索引数据失败", e);
                return;
            }
        }

        if(existWorldIndex != null){
            try(Connection conn = ds.getConnection()){
                if(conn == null || conn.isClosed()){
                    log.error("数据库连接异常，无法更新世界索引数据");
                    return;
                }

                var sql = "UPDATE WORLD_INDEX SET SEED = ?, TOTAL_TICK = ?, TEMPLATE_STD_REG_NAME = ?, SPAWN_X = ?, SPAWN_Y = ?, SPAWN_Z = ? WHERE ID = ?";
                try(PreparedStatement stmt = conn.prepareStatement(sql)){
                    stmt.setString(1, dto.getSeed());
                    stmt.setLong(2, dto.getTotalTick());
                    stmt.setString(3, dto.getTemplateStdRegName());
                    stmt.setInt(4, 0);
                    stmt.setInt(5, 0);
                    stmt.setInt(6, 0);
                    stmt.setLong(7, existWorldIndex.getId());
                    stmt.executeUpdate();
                    log.info("为世界 {} 更新归档记录", dto.getName());
                }
            }catch(SQLException e){
                log.error("更新世界索引数据失败", e);
                return;
            }
        }

    }


    /**
     * 加载对应世界名称的索引数据
     * @param worldName 世界名称
     * @return 世界索引数据 不存在时返回null
     */
    public ArchiveWorldIndexVo loadWorldIndex(String worldName){ 

        if(StringUtils.isBlank(worldName)){
            log.error("世界名称不能为空");
            return null;
        }

        var ds = archiveManager.getDataSource();
        if(ds == null){
            log.error("归档管理器当前未连接到归档，无法加载世界索引数据");
            return null;
        }

        try(Connection conn = ds.getConnection()){
            if(conn == null || conn.isClosed()){
                log.error("数据库连接异常，无法加载世界索引数据");
                return null;
            }

            var sql = "SELECT * FROM WORLD_INDEX WHERE NAME = ?";
            try(PreparedStatement stmt = conn.prepareStatement(sql)){
                stmt.setString(1, worldName);
                try(ResultSet rs = stmt.executeQuery()){
                    if(!rs.next()){
                        return null;
                    }

                    var vo = new ArchiveWorldIndexVo();
                    vo.setId(rs.getLong("ID"));
                    vo.setName(rs.getString("NAME"));
                    vo.setSeed(rs.getString("SEED"));
                    vo.setTotalTick(rs.getLong("TOTAL_TICK"));
                    vo.setTemplateStdRegName(rs.getString("TEMPLATE_STD_REG_NAME"));
                    return vo;
                }
            }
        }catch(SQLException e){
            log.error("加载世界索引数据失败", e);
            return null;
        }

    }



    


}
