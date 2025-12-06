package com.ksptool.ourcraft.server.archive;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import com.ksptool.ourcraft.server.archive.model.ArchiveVo;
import com.ksptool.ourcraft.sharedcore.enums.EngineDefault;

import com.ksptool.ourcraft.sharedcore.GlobalService;
import org.apache.commons.lang3.StringUtils;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * 归档管理器，负责管理全部归档文件的加载、保存、删除等操作
 */
@Slf4j
@Getter@Setter
public class ArchiveService implements GlobalService {

    //当前运行目录
    private String CURRENT_RUN_DIR = System.getProperty("user.dir");

    //归档根目录路径
    private String ARCHIVE_DIR = "archives";

    //归档索引数据库
    private String ARCHIVE_INDEX_DB_NAME = "archive_index";

    //归档索引数据库连接池
    private DataSource dataSource;

    //当前连接的归档名称
    private String currentArchiveName;

    //归档调色板管理器
    private ArchivePaletteService paletteService;

    //归档玩家管理器
    private ArchivePlayerService playerService;

    //归档世界管理器
    private ArchiveWorldService worldService;

    //归档区块管理器
    private ArchiveSuperChunkService chunkService;

    public ArchiveService(){
        this.paletteService = new ArchivePaletteService(this);
        this.playerService = new ArchivePlayerService(this);
        this.chunkService = new ArchiveSuperChunkService(this);
        this.worldService = new ArchiveWorldService(this, this.paletteService, this.chunkService);
    }




    /**
     * 判断是否连接到归档索引
     * @return 是否连接到归档索引
     */
    public boolean isConnectedArchiveIndex(){
        return dataSource != null;
    }

    /**
     * 连接归档索引
     */
    public void connectArchiveIndex(String archiveName){

        if(dataSource != null){
            log.warn("归档管理器当前已连接到一个归档索引,如果要连接到另一个归档索引,请先断开当前连接");
            return;
        }

        //检查归档根目录是否存在
        if(!existsArchive(archiveName)){
            createArchiveRootDir();
        }

        this.currentArchiveName = archiveName;

        var jdbcUrl = "jdbc:h2:file:" + getArchiveIndexAbsolutePath() + ";MODE=MySQL;AUTO_SERVER=TRUE";

        log.info("正在连接归档索引数据库: {}", jdbcUrl);
        HikariConfig config = new HikariConfig();
        // 使用 H2 文件模式，AUTO_SERVER=TRUE 允许在游戏运行时外部工具也能连接查看数据
        config.setJdbcUrl(jdbcUrl); 
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        dataSource = new HikariDataSource(config);

        //创建归档索引数据库表
        createTables();

        //写入归档基础信息
        createArchiveInfo();
        
        log.info("归档索引数据库连接成功 地址: {}", jdbcUrl);
    }

    /**
     * 获取归档快照
     * @param archiveName 归档名称
     * @return 归档快照
     */
    public ArchiveVo loadArchive(String archiveName) {

        this.currentArchiveName = archiveName;

        String errorMessage = validateArchiveIntegrity();

        if(StringUtils.isNotBlank(errorMessage)){
            log.error("归档完整性校验失败: {} 归档可能已经损坏，无法加载!", errorMessage);
            return null;
        }

        //打开归档索引
        connectArchiveIndex(archiveName);

        //创建归档索引数据库表
        createTables();

        //加载归档基础信息
        var vo = new ArchiveVo();

        try(Connection conn = dataSource.getConnection()){
            if(conn == null || conn.isClosed()){
                log.error("数据库连接异常，无法加载归档基础信息");
                return null;
            }

            try(Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT * FROM ARCHIVE_INFO")){
                if(rs.next()){
                    vo.setName(rs.getString("NAME"));
                    vo.setVersion(rs.getString("VERSION"));
                    vo.setCreateTime(rs.getTimestamp("CREATE_TIME").toLocalDateTime());
                    vo.setUpdateTime(rs.getTimestamp("UPDATE_TIME").toLocalDateTime());
                }
            }

            return vo;

        }catch(SQLException e){
            log.error("加载归档基础信息失败", e);
            return null;
        }

    }



    /**
     * 断开归档索引
     */
    public void disconnectArchiveIndex(){

        log.info("正在断开归档索引数据库连接 : {}", currentArchiveName);

        if(dataSource != null){
            
            //关闭连接池
            if(dataSource instanceof HikariDataSource){
                if(!((HikariDataSource) dataSource).isRunning()){
                    return;
                }
                ((HikariDataSource) dataSource).close();
            }
            
            dataSource = null;
        }

        log.info("归档索引数据库连接断开成功 : {}", currentArchiveName);
    }


    /**
     * 判断归档是否存在
     * @param archiveName 归档名称
     * @return 归档是否存在
     */
    public boolean existsArchive(String archiveName){
        
        if(StringUtils.isBlank(archiveName)){
            return false;
        }

        //检查归档根目录是否存在
        File archiveDir = new File(CURRENT_RUN_DIR + File.separator + ARCHIVE_DIR + File.separator + archiveName);
        if(!archiveDir.exists() || !archiveDir.isDirectory()){
            return false;
        }

        //检查归档索引数据库文件是否存在
        File archiveIndexDbFile = new File(CURRENT_RUN_DIR + File.separator + ARCHIVE_DIR + File.separator + archiveName + File.separator + ARCHIVE_INDEX_DB_NAME + ".mv.db");
        if(!archiveIndexDbFile.exists() || !archiveIndexDbFile.isFile()){
            return false;
        }

        return true;
    }

    /**
     * 校验归档完整性
     * @return 错误信息，如果完整性校验通过，则返回null
     */
    public String validateArchiveIntegrity(){
        
        if(StringUtils.isBlank(currentArchiveName)){
            log.error("归档名称为空，无法校验完整性");
            return "归档名称为空，无法校验完整性";
        }

        File archiveDir = new File(getArchiveRootDirAbsolutePath());
        if(!archiveDir.exists() || !archiveDir.isDirectory()){
            log.error("归档目录不存在: {}", archiveDir.getAbsolutePath());
            return "归档目录不存在: " + archiveDir.getAbsolutePath();
        }

        // 检查索引数据库
        File archiveIndexDbFile = new File(getArchiveIndexAbsolutePath() + ".mv.db");
        if(!archiveIndexDbFile.exists() || !archiveIndexDbFile.isFile()){
            return "归档索引数据库文件不存在: " + archiveIndexDbFile.getAbsolutePath();
        }

        return null;
    }

    /**
     * 创建归档索引数据库表 如果表不存在则创建
     */
    public void createTables(){

        if(dataSource == null){
            log.error("数据源未连接，无法创建表");
            return;
        }

        try(Connection conn = dataSource.getConnection()){
            if(conn == null || conn.isClosed()){
                log.error("数据库连接异常，无法创建表");
                return;
            }

            try(Statement stmt = conn.createStatement()){

                var createArchiveInfoTable = """
                    CREATE TABLE IF NOT EXISTS PUBLIC.ARCHIVE_INFO (
                        ID BIGINT NOT NULL AUTO_INCREMENT,
                        NAME VARCHAR(128) NOT NULL,
                        VERSION VARCHAR(64) NOT NULL,
                        CREATE_TIME TIMESTAMP WITH TIME ZONE NOT NULL,
                        UPDATE_TIME TIMESTAMP WITH TIME ZONE NOT NULL,
                        CONSTRAINT ARCHIVE_INFO_PK PRIMARY KEY (ID)
                    );
                    COMMENT ON TABLE PUBLIC.ARCHIVE_INFO IS '归档基础信息表，存储归档的基本信息';
                    COMMENT ON COLUMN PUBLIC.ARCHIVE_INFO.ID IS '归档记录的唯一标识符（主键）';
                    COMMENT ON COLUMN PUBLIC.ARCHIVE_INFO.NAME IS '归档名称';
                    COMMENT ON COLUMN PUBLIC.ARCHIVE_INFO.VERSION IS '归档版本';
                    COMMENT ON COLUMN PUBLIC.ARCHIVE_INFO.CREATE_TIME IS '归档创建时间';
                    COMMENT ON COLUMN PUBLIC.ARCHIVE_INFO.UPDATE_TIME IS '归档更新时间';
                    """;

                var createGlobalPaletteTable = """
                    CREATE TABLE IF NOT EXISTS PUBLIC.GLOBAL_PALETTE (
                        ID BIGINT NOT NULL AUTO_INCREMENT,
                        STD_REG_NAME VARCHAR(512) NOT NULL,
                        PROPERTIES BLOB,
                        CREATE_TIME TIMESTAMP WITH TIME ZONE NOT NULL,
                        CONSTRAINT GLOBAL_PALETTE_PK PRIMARY KEY (ID)
                    );
                    COMMENT ON TABLE PUBLIC.GLOBAL_PALETTE IS '全局调色板表，存储全局调色板信息';
                    COMMENT ON COLUMN PUBLIC.GLOBAL_PALETTE.ID IS '调色板ID';
                    COMMENT ON COLUMN PUBLIC.GLOBAL_PALETTE.STD_REG_NAME IS '方块标准注册名';
                    COMMENT ON COLUMN PUBLIC.GLOBAL_PALETTE.PROPERTIES IS '方块属性';
                    COMMENT ON COLUMN PUBLIC.GLOBAL_PALETTE.CREATE_TIME IS '创建时间';
                    """;

                var createPlayerIndexTable = """
                    CREATE TABLE IF NOT EXISTS PUBLIC.PLAYER_INDEX (
                        ID BIGINT NOT NULL AUTO_INCREMENT,
                        UUID VARCHAR(128) NOT NULL,
                        NAME VARCHAR(128) NOT NULL,
                        WORLD_NAME VARCHAR(128) NOT NULL,
                        LOGIN_COUNT INTEGER NOT NULL,
                        LAST_LOGIN_TIME TIMESTAMP WITH TIME ZONE NOT NULL,
                        POS_X NUMERIC(32,16) NOT NULL,
                        POS_Y NUMERIC(32,16) NOT NULL,
                        POS_Z NUMERIC(32,16),
                        PITCH NUMERIC(32,16) NOT NULL,
                        YAW NUMERIC(32,16) NOT NULL,
                        HEALTH INTEGER NOT NULL,
                        HUNGRY INTEGER NOT NULL,
                        EXP BIGINT NOT NULL,
                        BIN_DATA BLOB,
                        CREATE_TIME TIMESTAMP WITH TIME ZONE NOT NULL,
                        CONSTRAINT PLAYER_INDEX_PK PRIMARY KEY (ID)
                    );
                    COMMENT ON TABLE PUBLIC.PLAYER_INDEX IS '玩家索引表';
                    COMMENT ON COLUMN PUBLIC.PLAYER_INDEX.ID IS '玩家记录的唯一标识符（主键）';
                    COMMENT ON COLUMN PUBLIC.PLAYER_INDEX.UUID IS '玩家UUID';
                    COMMENT ON COLUMN PUBLIC.PLAYER_INDEX.NAME IS '玩家名称';
                    COMMENT ON COLUMN PUBLIC.PLAYER_INDEX.WORLD_NAME IS '玩家所在世界';
                    COMMENT ON COLUMN PUBLIC.PLAYER_INDEX.LOGIN_COUNT IS '登录次数';
                    COMMENT ON COLUMN PUBLIC.PLAYER_INDEX.LAST_LOGIN_TIME IS '最后登录时间';
                    COMMENT ON COLUMN PUBLIC.PLAYER_INDEX.POS_X IS '玩家位置X';
                    COMMENT ON COLUMN PUBLIC.PLAYER_INDEX.POS_Y IS '玩家位置Y';
                    COMMENT ON COLUMN PUBLIC.PLAYER_INDEX.POS_Z IS '玩家位置Z';
                    COMMENT ON COLUMN PUBLIC.PLAYER_INDEX.PITCH IS '玩家朝向Pitch';
                    COMMENT ON COLUMN PUBLIC.PLAYER_INDEX.YAW IS '玩家朝向Yaw';
                    COMMENT ON COLUMN PUBLIC.PLAYER_INDEX.HEALTH IS '玩家血量';
                    COMMENT ON COLUMN PUBLIC.PLAYER_INDEX.HUNGRY IS '玩家饥饿度';
                    COMMENT ON COLUMN PUBLIC.PLAYER_INDEX.EXP IS '玩家经验';
                    COMMENT ON COLUMN PUBLIC.PLAYER_INDEX.BIN_DATA IS '玩家数据';
                    COMMENT ON COLUMN PUBLIC.PLAYER_INDEX.CREATE_TIME IS '创建时间';
                    """;

                var createWorldIndexTable = """
                    CREATE TABLE PUBLIC.WORLD_INDEX (
                    	ID BIGINT NOT NULL AUTO_INCREMENT,
                    	NAME CHARACTER VARYING(128) NOT NULL,
                    	SEED CHARACTER VARYING(512) NOT NULL,
                    	TOTAL_TICK BIGINT NOT NULL,
                    	TEMPLATE_STD_REG_NAME CHARACTER VARYING(512) NOT NULL,
                    	SPAWN_X INTEGER NOT NULL,
                    	SPAWN_Y INTEGER NOT NULL,
                    	SPAWN_Z INTEGER NOT NULL,
                        SPAWN_CREATED INTEGER NOT NULL,
                        CREATE_TIME TIMESTAMP WITH TIME ZONE NOT NULL,
                    	CONSTRAINT WORLD_INDEX_PK PRIMARY KEY (ID)
                    );
                    COMMENT ON TABLE PUBLIC.WORLD_INDEX IS '世界索引表，存储所有已创建世界的基本信息';
                    COMMENT ON COLUMN PUBLIC.WORLD_INDEX.ID IS '世界记录的唯一标识符（主键）';
                    COMMENT ON COLUMN PUBLIC.WORLD_INDEX.NAME IS '世界唯一标识符(世界名称)';
                    COMMENT ON COLUMN PUBLIC.WORLD_INDEX.SEED IS '世界生成的随机种子';
                    COMMENT ON COLUMN PUBLIC.WORLD_INDEX.TOTAL_TICK IS '世界运行的总Action数';
                    COMMENT ON COLUMN PUBLIC.WORLD_INDEX.TEMPLATE_STD_REG_NAME IS '世界模板标准注册名';
                    COMMENT ON COLUMN PUBLIC.WORLD_INDEX.SPAWN_X IS '玩家默认出生点的 X 坐标';
                    COMMENT ON COLUMN PUBLIC.WORLD_INDEX.SPAWN_Y IS '玩家默认出生点的 Y 坐标';
                    COMMENT ON COLUMN PUBLIC.WORLD_INDEX.SPAWN_Z IS '玩家默认出生点的 Z 坐标';
                    COMMENT ON COLUMN PUBLIC.WORLD_INDEX.SPAWN_CREATED IS '玩家默认出生点是否已被创建/初始化（0=否, 1=是）';
                    COMMENT ON COLUMN PUBLIC.WORLD_INDEX.CREATE_TIME IS '世界创建时间';
                    """;

                var createChunkEntityTable = """
                        CREATE TABLE PUBLIC.CHUNK_ENTITY (
                        ID BIGINT NOT NULL AUTO_INCREMENT,
                        WORLD_ID BIGINT NOT NULL,
                        WORLD_NAME CHARACTER VARYING(128) NOT NULL,
                        CHUNK_X INTEGER NOT NULL,
                        CHUNK_Z INTEGER NOT NULL,
                        -- 甚至可以记录关联的 SCA 文件名，方便调试
                        SCA_FILE_NAME CHARACTER VARYING(128) NOT NULL,
                        ENTITY_COUNT INTEGER,
                        ENTITY_BIN_DATA BINARY LARGE OBJECT, -- 实体数据的序列化二进制
                        VERSION CHARACTER VARYING(32) NOT NULL,
                        CREATE_TIME TIMESTAMP WITH TIME ZONE NOT NULL,
                        UPDATE_TIME TIMESTAMP WITH TIME ZONE NOT NULL,
                        CONSTRAINT CHUNK_ENTITY_PK PRIMARY KEY (ID)
                    );
                    -- 唯一索引确保一个区块只有一条实体记录
                    CREATE UNIQUE INDEX IDX_UNI_CHUNK_ENTITY_WORLD_ID_NAME_X_Z ON PUBLIC.CHUNK_ENTITY (WORLD_ID, WORLD_NAME, CHUNK_X, CHUNK_Z);
                    """;

                if(!tableExists(stmt, "ARCHIVE_INFO")){
                    stmt.execute(createArchiveInfoTable);
                    log.info("创建表 ARCHIVE_INFO 成功");
                }

                if(!tableExists(stmt, "GLOBAL_PALETTE")){
                    stmt.execute(createGlobalPaletteTable);
                    log.info("创建表 GLOBAL_PALETTE 成功");
                }

                if(!tableExists(stmt, "PLAYER_INDEX")){
                    stmt.execute(createPlayerIndexTable);
                    log.info("创建表 PLAYER_INDEX 成功");
                }

                if(!tableExists(stmt, "WORLD_INDEX")){
                    stmt.execute(createWorldIndexTable);
                    log.info("创建表 WORLD_INDEX 成功");
                }

                if(!tableExists(stmt, "CHUNK_ENTITY")){
                    stmt.execute(createChunkEntityTable);
                    log.info("创建表 CHUNK_ENTITY 成功");
                }

            }

        } catch(SQLException e){
            log.error("创建归档索引数据库表失败", e);
        }

    }

    /**
     * 向ARCHIVE_INFO表写入归档基础信息
     */
    public void createArchiveInfo(){
        
        if(dataSource == null){
            log.error("数据源未连接，无法写入归档基础信息");
            return;
        }

        //初始化归档信息
        var version = EngineDefault.ENGINE_VERSION;
        var createTime = LocalDateTime.now();
        var updateTime = LocalDateTime.now();

        //写入归档信息
        try(Connection conn = dataSource.getConnection()){
            if(conn == null || conn.isClosed()){
                log.error("数据库连接异常，无法写入归档信息");
                return;
            }

            //统计是否已有归档信息
            String countSql = "SELECT COUNT(*) FROM ARCHIVE_INFO";

            try(Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(countSql)){
                if(rs.next()){
                    int count = rs.getInt(1);
                    if(count > 0){
                        //log.warn("归档信息已存在.");
                        return;
                    }
                }
            }

            try(PreparedStatement stmt = conn.prepareStatement("INSERT INTO ARCHIVE_INFO (NAME, VERSION, CREATE_TIME, UPDATE_TIME) VALUES (?, ?, ?, ?)")){
                stmt.setString(1, currentArchiveName);
                stmt.setString(2, version);
                stmt.setTimestamp(3, Timestamp.from(createTime.toInstant(ZoneOffset.UTC)));
                stmt.setTimestamp(4, Timestamp.from(updateTime.toInstant(ZoneOffset.UTC)));
                stmt.executeUpdate();
            }

            log.info("写入归档信息成功");
            
        }catch(SQLException e){
            log.error("写入归档信息失败", e);
            return;
        }
    }


    /**
     * 检查表是否存在
     * @param stmt Statement对象
     * @param tableName 表名
     * @return 表是否存在
     */
    private boolean tableExists(Statement stmt, String tableName) throws SQLException{
        String checkTableSql = """
            SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME = '%s'
            """.formatted(tableName);
        
        try(ResultSet rs = stmt.executeQuery(checkTableSql)){
            if(rs.next()){
                return rs.getInt(1) > 0;
            }
        }
        return false;
    }


    /**
     * 获取归档索引数据库文件绝对路径(不包含后缀mv.db)
     * @return 归档索引数据库文件绝对路径
     */
    public String getArchiveIndexAbsolutePath(){
        return CURRENT_RUN_DIR + File.separator + ARCHIVE_DIR + File.separator + currentArchiveName + File.separator + ARCHIVE_INDEX_DB_NAME;
    }

    /**
     * 获取归档根目录绝对路径
     * @return 归档根目录绝对路径
     */
    public String getArchiveRootDirAbsolutePath(){
        return CURRENT_RUN_DIR + File.separator + ARCHIVE_DIR + File.separator + currentArchiveName;
    }

    /**
     * 获取归档SCA文件夹绝对路径
     * @param worldName 世界名称
     * @return 归档SCA文件夹绝对路径 如archives/archiveName/[worldName]
     */
    public String getArchiveScaDirAbsolutePath(String worldName){
        return getArchiveRootDirAbsolutePath() + File.separator + worldName;
    }

    /**
     * 创建归档根目录
     */
    public void createArchiveRootDir() {

        if(StringUtils.isBlank(currentArchiveName)){
            log.error("归档名称不能为空");
            return;
        }

        //检查归档Root
        Path archiveRootDir = Paths.get(CURRENT_RUN_DIR, ARCHIVE_DIR, currentArchiveName);

        //不存在则创建
        if(!Files.exists(archiveRootDir)){
            try {
                Files.createDirectories(archiveRootDir);
                log.info("创建新的归档根目录: {}", archiveRootDir.toString());
            } catch (IOException e) {
                log.error("创建归档根目录失败: {}", archiveRootDir.toString(), e);
                return;
            }
        }

    }

}
