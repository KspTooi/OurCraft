package com.ksptool.mycraft.entity;

import com.ksptool.mycraft.world.World;
import lombok.Getter;
import org.joml.Vector3f;

import java.util.Objects;
import java.util.UUID;

/**
 * 实体基类，定义实体的基本属性和行为
 */
@Getter
public abstract class Entity {
    protected final World world;
    protected final UUID uniqueId;
    protected final Vector3f position;
    protected final Vector3f velocity;
    protected boolean onGround;
    protected BoundingBox boundingBox;
    protected boolean isDead;
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

    public void setOnGround(boolean onGround) {
        this.onGround = onGround;
    }

    public void setBoundingBox(BoundingBox boundingBox) {
        this.boundingBox = boundingBox;
    }

    public void setDead(boolean dead) {
        this.isDead = dead;
    }

    public void markDirty(boolean isDirty) {
        this.isDirty = isDirty;
        if (isDirty && world != null) {
            int chunkX = (int) Math.floor(position.x / com.ksptool.mycraft.world.Chunk.CHUNK_SIZE);
            int chunkZ = (int) Math.floor(position.z / com.ksptool.mycraft.world.Chunk.CHUNK_SIZE);
            com.ksptool.mycraft.world.Chunk chunk = world.getChunk(chunkX, chunkZ);
            if (chunk != null) {
                chunk.markEntitiesDirty(true);
            }
        }
    }

    public boolean isDirty() {
        return isDirty;
    }
}

