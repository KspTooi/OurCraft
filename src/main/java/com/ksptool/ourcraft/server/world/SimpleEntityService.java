package com.ksptool.ourcraft.server.world;

import com.ksptool.ourcraft.server.entity.ServerEntity;
import com.ksptool.ourcraft.server.entity.ServerPlayer;
import com.ksptool.ourcraft.sharedcore.utils.EntitySerializer;
import com.ksptool.ourcraft.sharedcore.world.SharedWorld;
import com.ksptool.ourcraft.sharedcore.world.WorldService;
import org.apache.commons.lang3.StringUtils;
import org.joml.Vector3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 实体管理器，负责实体的生命周期管理
 */
public class SimpleEntityService extends WorldService {
    private static final Logger logger = LoggerFactory.getLogger(SimpleEntityService.class);
    
    private final ServerWorld world;
    private final CopyOnWriteArrayList<ServerEntity> entities;
    private String saveName;

    //玩家会话ID->玩家实体
    private final ConcurrentHashMap<Long, ServerPlayer> playerSessions = new ConcurrentHashMap<>();
    
    public SimpleEntityService(ServerWorld world) {
        this.world = world;
        this.entities = new CopyOnWriteArrayList<>();
    }

    public void addEntity(ServerEntity entity) {
        entities.add(entity);
        
        //如果实体是玩家，则添加到玩家会话ID到玩家实体的映射
        if (entity instanceof ServerPlayer pl) {
            playerSessions.put(pl.getSession().getId(), pl);
        }
        entity.markDirty(true);
    }

    public void removeEntity(ServerEntity entity) {
        entities.remove(entity);

        //如果实体是玩家，则从玩家会话ID到玩家实体的映射中移除
        if (entity instanceof ServerPlayer pl) {
            playerSessions.remove(pl.getSession().getId());
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
    


    public void update(float delta) {
        for (ServerEntity entity : entities) {
            entity.update(delta);
        }
    }

    @Override
    public void action(double delta, SharedWorld world) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'action'");
    }
}

