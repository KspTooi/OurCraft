package com.ksptool.mycraft.entity;

import com.ksptool.mycraft.world.Chunk;
import com.ksptool.mycraft.world.World;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

import java.util.Objects;
import java.util.UUID;

/**
 * 实体基类，定义实体的基本属性和行为
 */
@Getter
public abstract class Entity {

    //世界
    protected final World world;

    //唯一ID
    protected final UUID uniqueId;

    //位置
    protected final Vector3f position;

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

    public Entity(World world) {
        this(world, UUID.randomUUID());
    }

    public Entity(World world, UUID uniqueId) {
        this.world = Objects.requireNonNull(world);
        this.uniqueId = uniqueId != null ? uniqueId : UUID.randomUUID();
        this.position = new Vector3f();
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
            int chunkX = (int) Math.floor(position.x / Chunk.CHUNK_SIZE);
            int chunkZ = (int) Math.floor(position.z / Chunk.CHUNK_SIZE);
            Chunk chunk = world.getChunk(chunkX, chunkZ);
            if (chunk != null) {
                chunk.markEntitiesDirty(true);
            }
        }
    }

}

