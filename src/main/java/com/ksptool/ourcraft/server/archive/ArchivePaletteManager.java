package com.ksptool.ourcraft.server.archive;

import com.ksptool.ourcraft.server.archive.model.GlobalPaletteProperty;
import com.ksptool.ourcraft.sharedcore.GlobalPalette;
import com.ksptool.ourcraft.sharedcore.blocks.inner.SharedBlock;
import com.ksptool.ourcraft.sharedcore.network.KryoManager;
import com.ksptool.ourcraft.sharedcore.world.BlockState;
import com.ksptool.ourcraft.sharedcore.Registry;
import com.ksptool.ourcraft.sharedcore.world.properties.BlockProperty;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.sql.rowset.serial.SerialBlob;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Slf4j
public class ArchivePaletteManager {

    // 归档管理器
    private final ArchiveManager archiveManager;

    public ArchivePaletteManager(ArchiveManager archiveManager){
        this.archiveManager = archiveManager;
    }

    /**
     * 保存全局调色板
     * 使用 MERGE 语法避免主键冲突，并启用批处理提高性能
     * @param globalPalette 全局调色板
     */
    public void saveGlobalPalette(GlobalPalette globalPalette){

        // 获取归档数据库连接池
        var dataSource = archiveManager.getDataSource();
        var currentArchiveName = archiveManager.getCurrentArchiveName();

        if(dataSource == null){
            log.error("归档管理器当前未连接到归档，无法保存全局调色板");
            return;
        }

        if(globalPalette == null){
            log.error("全局调色板不能为空");
            return;
        }

        if(StringUtils.isBlank(currentArchiveName)){
            log.error("当前未连接到归档，无法保存全局调色板");
            return;
        }

        try(Connection conn = dataSource.getConnection()){
            if(conn == null || conn.isClosed()){
                log.error("数据库连接异常，无法保存全局调色板");
                return;
            }

            // 关闭自动提交以启用事务，提高批量写入性能
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            //使用 MERGE INTO ... KEY(...) 语法
            //如果 STD_REG_NAME 已存在则更新，不存在则插入，完美解决唯一键冲突
            String sql = "MERGE INTO GLOBAL_PALETTE (ID, STD_REG_NAME, PROPERTIES, CREATE_TIME) KEY(ID) VALUES (?, ?, ?, ?)";

            try(PreparedStatement stmt = conn.prepareStatement(sql)){

                var count = 0;
                // 重用 ByteArrayOutputStream 减少内存分配
                ByteArrayOutputStream baos = new ByteArrayOutputStream(512); 

                for(int i = 0; i < globalPalette.getStateCount(); i++){
                    BlockState state = globalPalette.getState(i);

                    stmt.setInt(1, globalPalette.getStateId(state));
                    //设置标准注册名
                    stmt.setString(2, state.getSharedBlock().getStdRegName().getValue());

                    //序列化属性
                    var properties = new ArrayList<GlobalPaletteProperty>();
                    for(var entry : state.getProperties().entrySet()){
                        var property = new GlobalPaletteProperty();
                        property.setK(entry.getKey().getName());
                        property.setV(entry.getValue().toString());
                        properties.add(property);
                    }

                    baos.reset(); //重置流
                    KryoManager.writeObject(properties, baos);
                    stmt.setBlob(3, new SerialBlob(baos.toByteArray()));

                    //设置时间戳
                    stmt.setTimestamp(4, Timestamp.from(LocalDateTime.now().toInstant(ZoneOffset.UTC)));

                    //加入批处理
                    stmt.addBatch();
                    count++;
                    
                    //每500条提交一次，防止内存溢出
                    if (count % 500 == 0) {
                        stmt.executeBatch();
                    }
                }

                // 执行剩余的批处理
                stmt.executeBatch();
                
                // 提交事务
                conn.commit();
                conn.setAutoCommit(autoCommit); // 恢复现场

                log.info("全局调色板已同步至归档索引，共处理 {} 个状态", count);
            } catch (Exception e) {
                conn.rollback(); // 发生异常回滚
                throw e;
            }

        }catch(SQLException | IOException e) {
            log.error("保存全局调色板失败", e);
        }
    }

    /**
     * 加载全局调色板
     * @param globalPalette 全局调色板
     * @return 加载的调色板状态数
     */
    public int loadGlobalPalette(GlobalPalette globalPalette){

        // 获取归档数据库连接池
        var dataSource = archiveManager.getDataSource();
        var currentArchiveName = archiveManager.getCurrentArchiveName();

        if(dataSource == null){
            log.error("归档管理器当前未连接到归档，无法加载全局调色板");
            return 0;
        }

        if(globalPalette == null){
            log.error("全局调色板不能为空");
            return 0;
        }

        if(StringUtils.isBlank(currentArchiveName)){
            log.error("当前未连接到归档，无法加载全局调色板");
            return 0;
        }

        try(Connection conn = dataSource.getConnection()){
            if(conn == null || conn.isClosed()){
                log.error("数据库连接异常，无法加载全局调色板");
                return 0;
            }

            globalPalette.clear();
            Registry registry = Registry.getInstance();

            // 按ID排序确保加载顺序一致（这对调色板ID的稳定性至关重要）
            try(Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT STD_REG_NAME, PROPERTIES FROM GLOBAL_PALETTE ORDER BY ID")){

                int count = 0;

                while(rs.next()){
                    String stdRegName = rs.getString("STD_REG_NAME");
                    if(StringUtils.isBlank(stdRegName)){
                        continue;
                    }

                    SharedBlock sharedBlock = registry.getBlock(stdRegName);
                    if(sharedBlock == null){
                        log.warn("归档中引用了未知方块: {}, 可能已被移除或Mod缺失", stdRegName);
                        continue;
                    }

                    Blob propertiesBlob = rs.getBlob("PROPERTIES");
                    if(propertiesBlob == null){
                        BlockState state = new BlockState(sharedBlock, new HashMap<>());
                        // 确保加入顺序与数据库ID顺序一致
                        addStateToPalette(globalPalette, state);
                        count++;
                        continue;
                    }

                    byte[] blobBytes = propertiesBlob.getBytes(1, (int)propertiesBlob.length());
                    ByteArrayInputStream bais = new ByteArrayInputStream(blobBytes);

                    @SuppressWarnings("unchecked")
                    List<GlobalPaletteProperty> properties = (List<GlobalPaletteProperty>) KryoManager.readObject(bais);

                    Map<BlockProperty<?>, Comparable<?>> blockProperties = new HashMap<>();
                    if(properties != null){
                        for(GlobalPaletteProperty prop : properties){
                            if(prop == null || StringUtils.isBlank(prop.getK())){
                                continue;
                            }

                            String propName = prop.getK();
                            String propValueStr = prop.getV();

                            for(BlockProperty<?> blockProperty : sharedBlock.getProperties()){
                                if(blockProperty.getName().equals(propName)){
                                    Comparable<?> value = parsePropertyValue(blockProperty, propValueStr);
                                    if(value != null){
                                        blockProperties.put(blockProperty, value);
                                    }
                                    break;
                                }
                            }
                        }
                    }

                    BlockState state = new BlockState(sharedBlock, blockProperties);
                    addStateToPalette(globalPalette, state);
                    count++;
                }

                globalPalette.setBaked(true);
                log.info("加载全局调色板成功: 状态数={}", count);
                return count;
            }

        } catch(SQLException | IOException e){
            log.error("加载全局调色板失败", e);
            return 0;
        }
    }
    
    private void addStateToPalette(GlobalPalette palette, BlockState state) {
        palette.getStateList().add(state);
        palette.getStateToId().put(state, palette.getStateList().size() - 1);
    }

    private Comparable<?> parsePropertyValue(BlockProperty<?> property, String valueStr){
        Collection<?> allowedValues = property.getAllowedValues();
        if(allowedValues.isEmpty()){
            return null;
        }

        Object sample = allowedValues.iterator().next();
        if(sample instanceof Enum){
            for(Object val : allowedValues){
                if(val.toString().equals(valueStr)){
                    return (Comparable<?>) val;
                }
            }
        } else if(sample instanceof Boolean){
            return Boolean.parseBoolean(valueStr);
        } else if(sample instanceof Integer){
            return Integer.parseInt(valueStr);
        }

        return null;
    }
}