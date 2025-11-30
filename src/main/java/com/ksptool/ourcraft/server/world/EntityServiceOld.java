package com.ksptool.ourcraft.server.world;

import com.ksptool.ourcraft.server.entity.ServerEntity;
import com.ksptool.ourcraft.server.entity.ServerPlayer;
import com.ksptool.ourcraft.server.world.chunk.ServerChunkOld;
import com.ksptool.ourcraft.server.world.save.EntitySerializer;
import com.ksptool.ourcraft.server.world.save.RegionFile;
import com.ksptool.ourcraft.server.world.save.RegionManager;
import org.apache.commons.lang3.StringUtils;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 实体管理器，负责实体的生命周期管理
 */
public class EntityServiceOld {
    private static final Logger logger = LoggerFactory.getLogger(EntityServiceOld.class);
    
    private final ServerWorld world;
    private final CopyOnWriteArrayList<ServerEntity> entities;
    private RegionManager entityRegionManager;
    private String saveName;

    //玩家会话ID->玩家实体
    private final ConcurrentHashMap<Long, ServerPlayer> playerSessions = new ConcurrentHashMap<>();
    
    public EntityServiceOld(ServerWorld world) {
        this.world = world;
        this.entities = new CopyOnWriteArrayList<>();
    }
    
    public void setSaveName(String saveName) {
        this.saveName = saveName;
    }
    
    public void setEntityRegionManager(RegionManager entityRegionManager) {
        this.entityRegionManager = entityRegionManager;
    }
    
    public RegionManager getEntityRegionManager() {
        return entityRegionManager;
    }
    
    public void addEntity(ServerEntity entity) {
        entities.add(entity);
        
        //如果实体是玩家，则添加到玩家会话ID到玩家实体的映射
        if (entity instanceof ServerPlayer pl) {
            playerSessions.put(pl.getSessionId(), pl);
        }
        entity.markDirty(true);
    }

    public void removeEntity(ServerEntity entity) {
        entities.remove(entity);

        //如果实体是玩家，则从玩家会话ID到玩家实体的映射中移除
        if (entity instanceof ServerPlayer pl) {
            playerSessions.remove(pl.getSessionId());
        }
    }

    /**
     * 根据会话ID获取玩家实体
     * @param sessionId 会话ID
     * @return 玩家实体
     */
    public ServerPlayer getPlayerBySessionId(long sessionId) {
        return playerSessions.get(sessionId);
    }

    public List<ServerEntity> getEntities() {
        return entities;
    }
    
    public void loadEntitiesForChunk(int chunkX, int chunkZ) {
        if (entityRegionManager == null) {
            return;
        }
        
        try {
            int regionX = RegionManager.getRegionX(chunkX);
            int regionZ = RegionManager.getRegionZ(chunkZ);
            int localX = RegionManager.getLocalChunkX(chunkX);
            int localZ = RegionManager.getLocalChunkZ(chunkZ);
            
            RegionFile entityRegionFile = entityRegionManager.getRegionFile(regionX, regionZ);
            entityRegionFile.open();
            
            byte[] compressedData = entityRegionFile.readChunk(localX, localZ);
            if (compressedData == null) {
                return;
            }
            
            List<ServerEntity> loadedEntities = EntitySerializer.deserialize(compressedData, world);
            if (loadedEntities != null && !loadedEntities.isEmpty()) {
                logger.debug("从区块 [{},{}] 加载了 {} 个实体", chunkX, chunkZ, loadedEntities.size());
                for (ServerEntity entity : loadedEntities) {
                    if (!entities.contains(entity)) {
                        //entity.getPreviousPosition().set(entity.getPosition());
                        //if (entity instanceof ServerPlayer) {
                        //    ServerPlayer player = (ServerPlayer) entity;
                        //    player.setPreviousYaw(player.getYaw());
                        //    player.setPreviousPitch(player.getPitch());
                        //}
                        entities.add(entity);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("加载实体失败 [{},{}]", chunkX, chunkZ, e);
        }
    }
    
    public void saveEntitiesForChunk(int chunkX, int chunkZ) {
        if (entityRegionManager == null || StringUtils.isBlank(saveName)) {
            return;
        }
        
        try {
            List<ServerEntity> chunkEntities = new ArrayList<>();
            float chunkMinX = chunkX * ServerChunkOld.CHUNK_SIZE;
            float chunkMaxX = chunkMinX + ServerChunkOld.CHUNK_SIZE;
            float chunkMinZ = chunkZ * ServerChunkOld.CHUNK_SIZE;
            float chunkMaxZ = chunkMinZ + ServerChunkOld.CHUNK_SIZE;
            
            for (ServerEntity entity : entities) {
                Vector3d pos = entity.getPosition();
                if (pos.x >= chunkMinX && pos.x < chunkMaxX && 
                    pos.z >= chunkMinZ && pos.z < chunkMaxZ) {
                    chunkEntities.add(entity);
                }
            }
            
            if (chunkEntities.isEmpty()) {
                return;
            }
            
            byte[] compressedData = EntitySerializer.serialize(chunkEntities);
            
            int regionX = RegionManager.getRegionX(chunkX);
            int regionZ = RegionManager.getRegionZ(chunkZ);
            int localX = RegionManager.getLocalChunkX(chunkX);
            int localZ = RegionManager.getLocalChunkZ(chunkZ);
            
            RegionFile entityRegionFile = entityRegionManager.getRegionFile(regionX, regionZ);
            entityRegionFile.open();
            entityRegionFile.writeChunk(localX, localZ, compressedData);
            logger.debug("成功保存区块 [{},{}] 的 {} 个实体", chunkX, chunkZ, chunkEntities.size());
        } catch (Exception e) {
            logger.error("保存实体失败 [{},{}]", chunkX, chunkZ, e);
        }
    }
    
    public void saveAllDirtyEntities() {
        if (entityRegionManager == null || StringUtils.isBlank(saveName)) {
            return;
        }
        
        try {
            int dirtyEntityChunkCount = 0;
            
            for (ServerEntity entity : entities) {
                Vector3d pos = entity.getPosition();
                int entityChunkX = (int) Math.floor(pos.x / ServerChunkOld.CHUNK_SIZE);
                int entityChunkZ = (int) Math.floor(pos.z / ServerChunkOld.CHUNK_SIZE);
                
                ServerChunkOld chunk = world.getChunkManagerOld().getChunk(entityChunkX, entityChunkZ);
                if (chunk != null && chunk.areEntitiesDirty()) {
                    logger.debug("保存脏实体区块 [{},{}]", entityChunkX, entityChunkZ);
                    saveEntitiesForChunk(entityChunkX, entityChunkZ);
                    chunk.markEntitiesDirty(false);
                    dirtyEntityChunkCount++;
                    
                    entity.markDirty(false);
                }
            }
            
            if (dirtyEntityChunkCount == 0) {
                logger.debug("没有需要保存的脏实体");
                return;
            }
            logger.info("保存完成: 脏实体区块数={}", dirtyEntityChunkCount);
        } catch (Exception e) {
            logger.error("保存实体失败", e);
        }
    }
    
    public void update(float delta) {
        for (ServerEntity entity : entities) {
            entity.update(delta);
        }
    }
}

