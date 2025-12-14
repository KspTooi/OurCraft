package com.ksptool.ourcraft.clientj.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3d;

import java.util.UUID;

/**
 * 客户端玩家实体类
 */
@Slf4j
@Getter
@Setter
public class ClientPlayer {

    private UUID uuid;
    private String name;
    private int health;
    private int hungry;
    
    private double posX;
    private double posY;
    private double posZ;
    private double yaw;
    private double pitch;
    
    private float groundAcceleration;
    private float airAcceleration;
    private float maxSpeed;

    public ClientPlayer() {
    }

    public ClientPlayer(String uuid, String name, int health, int hungry, 
                       double posX, double posY, double posZ, 
                       double yaw, double pitch,
                       float groundAcceleration, float airAcceleration, float maxSpeed) {
        this.uuid = UUID.fromString(uuid);
        this.name = name;
        this.health = health;
        this.hungry = hungry;
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.yaw = yaw;
        this.pitch = pitch;
        this.groundAcceleration = groundAcceleration;
        this.airAcceleration = airAcceleration;
        this.maxSpeed = maxSpeed;
    }

    public Vector3d getPosition() {
        return new Vector3d(posX, posY, posZ);
    }

    public void setPosition(Vector3d position) {
        this.posX = position.x;
        this.posY = position.y;
        this.posZ = position.z;
    }

}
