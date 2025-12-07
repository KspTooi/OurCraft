package com.ksptool.ourcraft.server.archive;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import com.ksptool.ourcraft.server.archive.model.ArchiveWorldIndexDto;
import com.ksptool.ourcraft.server.world.chunk.FlexServerChunk;
import com.ksptool.ourcraft.server.world.chunk.SimpleServerChunk;
import com.ksptool.ourcraft.server.world.chunk.SimpleChunkSerializer;
import com.ksptool.ourcraft.sharedcore.utils.FlexChunkSerializer;
import org.apache.commons.lang3.StringUtils;
import com.ksptool.ourcraft.server.archive.model.ArchiveWorldIndexVo;
import com.ksptool.ourcraft.server.world.ServerWorld;
import com.ksptool.ourcraft.sharedcore.GlobalPalette;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ArchiveWorldService {

    //归档管理器
    private final ArchiveService archiveService;

    //归档调色板管理器
    private final ArchivePaletteService paletteManager;

    //归档区块管理器
    private final ArchiveSuperChunkService chunkService;

    public ArchiveWorldService(ArchiveService archiveService, ArchivePaletteService paletteManager, ArchiveSuperChunkService chunkService){
        this.archiveService = archiveService;
        this.paletteManager = paletteManager;
        this.chunkService = chunkService;
    }

    public void saveWorld(ServerWorld world){

        if(world == null){
            log.error("世界不能为空");
            return;
        }

        var archiveName = archiveService.getCurrentArchiveName();
        
        if(StringUtils.isBlank(archiveName)){
            log.error("当前未连接到归档，无法保存世界数据");
            return;
        }

        //直接保存世界运行时到归档索引(归档索引不存在时自动创建)
        var existWorldIndex = loadWorldIndex(world.getName());

        var dto = new ArchiveWorldIndexDto();
        dto.setName(world.getName());
        dto.setSeed(world.getSeed());
        dto.setTotalTick(world.getSwts().getTotalActions());
        dto.setTemplateStdRegName(world.getTemplate().getStdRegName().toString());
        dto.setSpawnX(world.getDefaultSpawnPos().getX());
        dto.setSpawnY(world.getDefaultSpawnPos().getY());
        dto.setSpawnZ(world.getDefaultSpawnPos().getZ());
        dto.setDefaultSpawnCreated(0); //0:否, 1:是

        if(existWorldIndex != null){
            dto.setDefaultSpawnCreated(existWorldIndex.getDefaultSpawnCreated());
        }
        saveWorldIndex(dto);
        
        //保存当前的全局调色板数据
        paletteManager.saveGlobalPalette(GlobalPalette.getInstance());

        //保存当前的区块数据
        List<FlexServerChunk> dirtyChunks = world.getFscs().getDirtySnapshot();
        int chunkCount = 0;

        for (FlexServerChunk chunk : dirtyChunks) {
            byte[] compressedData = FlexChunkSerializer.serialize(chunk.getFlexChunkData());
            chunkService.writeChunk(world.getName(),chunk.getChunkPos(),compressedData);
            chunk.setDirty(false);
            chunkCount++;
        }

        //保存实体数据
        world.getSes().saveAllDirtyEntities();

        log.info("世界 {} 保存完成，保存区块数: {}", world.getName(), chunkCount);
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

        var ds = archiveService.getDataSource();
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

                var sql = "INSERT INTO WORLD_INDEX (NAME, SEED, TOTAL_TICK, TEMPLATE_STD_REG_NAME, SPAWN_X, SPAWN_Y, SPAWN_Z, SPAWN_CREATED, CREATE_TIME) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try(PreparedStatement stmt = conn.prepareStatement(sql)){
                    stmt.setString(1, dto.getName());
                    stmt.setString(2, dto.getSeed());
                    stmt.setLong(3, dto.getTotalTick());
                    stmt.setString(4, dto.getTemplateStdRegName());
                    stmt.setInt(5, dto.getSpawnX());
                    stmt.setInt(6, dto.getSpawnY());
                    stmt.setInt(7, dto.getSpawnZ());
                    stmt.setInt(8, dto.getDefaultSpawnCreated());
                    stmt.setTimestamp(9, Timestamp.valueOf(LocalDateTime.now()));
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

                var sql = "UPDATE WORLD_INDEX SET SEED = ?, TOTAL_TICK = ?, TEMPLATE_STD_REG_NAME = ?, SPAWN_X = ?, SPAWN_Y = ?, SPAWN_Z = ?, SPAWN_CREATED = ? WHERE ID = ?";
                try(PreparedStatement stmt = conn.prepareStatement(sql)){
                    stmt.setString(1, dto.getSeed());
                    stmt.setLong(2, dto.getTotalTick());
                    stmt.setString(3, dto.getTemplateStdRegName());
                    stmt.setInt(4, dto.getSpawnX());
                    stmt.setInt(5, dto.getSpawnY());
                    stmt.setInt(6, dto.getSpawnZ());
                    stmt.setInt(7, dto.getDefaultSpawnCreated());
                    stmt.setLong(8, existWorldIndex.getId());
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

        var ds = archiveService.getDataSource();
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
                    vo.setDefaultSpawnX(rs.getInt("SPAWN_X"));
                    vo.setDefaultSpawnY(rs.getInt("SPAWN_Y"));
                    vo.setDefaultSpawnZ(rs.getInt("SPAWN_Z"));
                    vo.setDefaultSpawnCreated(rs.getInt("SPAWN_CREATED"));
                    var timestamp = rs.getTimestamp("CREATE_TIME");
                    if(timestamp != null){
                        vo.setCreateTime(timestamp.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
                    }
                    return vo;
                }
            }
        }catch(SQLException e){
            log.error("加载世界索引数据失败", e);
            return null;
        }

    }



    


}
