package com.ksptool.mycraft.server.entity;

import com.ksptool.mycraft.sharedcore.BoundingBox;
import com.ksptool.mycraft.server.world.ServerChunk;
import com.ksptool.mycraft.server.world.ServerWorld;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * 服务端实体基类，定义实体的基本属性和行为
 */
@Getter
public abstract class ServerEntity {

    //世界（服务端）
    protected final ServerWorld world;

    //唯一ID
    protected final UUID uniqueId;

    //位置
    protected final Vector3f position;
    
    //上一逻辑刻的位置（用于插值）
    protected final Vector3f previousPosition;

    //速度
    protected final Vector3f velocity;

    //是否在地面上
    @Setter
    protected boolean onGround;

    //边界框
    @Setter
    protected BoundingBox boundingBox;

    //是否死亡
    @Setter
    protected boolean isDead;

    //是否脏（脏实体需要保存到磁盘）
    protected boolean isDirty = false;

    public ServerEntity(ServerWorld world) {
        this(world, UUID.randomUUID());
    }

    public ServerEntity(ServerWorld world, UUID uniqueId) {
        if (world == null) {
            throw new IllegalArgumentException("ServerEntity requires a non-null ServerWorld");
        }
        this.world = world;
        this.uniqueId = uniqueId != null ? uniqueId : UUID.randomUUID();
        this.position = new Vector3f();
        this.previousPosition = new Vector3f();
        this.velocity = new Vector3f();
        this.onGround = false;
        this.isDead = false;
    }

    public abstract void update(float delta);

    /**
     * 标记实体为脏
     * @param isDirty 是否脏
     */
    public void markDirty(boolean isDirty) {
        this.isDirty = isDirty;
        if (isDirty && world != null) {
            int chunkX = (int) Math.floor(position.x / ServerChunk.CHUNK_SIZE);
            int chunkZ = (int) Math.floor(position.z / ServerChunk.CHUNK_SIZE);
            ServerChunk chunk = world.getChunk(chunkX, chunkZ);
            if (chunk != null) {
                chunk.markEntitiesDirty(true);
            }
        }
    }
    
    public ServerWorld getWorld() {
        return world;
    }

}

