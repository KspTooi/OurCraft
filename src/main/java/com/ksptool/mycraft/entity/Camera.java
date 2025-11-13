package com.ksptool.mycraft.entity;

import lombok.Getter;
import lombok.Setter;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 相机类，负责计算视图矩阵和投影矩阵
 */
@Getter
public class Camera {
    
    //相机位置
    private final Vector3f position;

    //俯仰角
    private float pitch;

    //偏航角
    @Setter
    private float yaw;

    //翻滚角
    @Setter
    private float roll;

    //视图矩阵
    private final Matrix4f viewMatrix;

    //投影矩阵
    private final Matrix4f projectionMatrix;

    public Camera() {
        this.position = new Vector3f();
        this.viewMatrix = new Matrix4f();
        this.projectionMatrix = new Matrix4f();
    }

    public void update() {
        viewMatrix.identity();
        
        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);
        
        float cosYaw = (float) Math.cos(yawRad);
        float sinYaw = (float) Math.sin(yawRad);
        float cosPitch = (float) Math.cos(pitchRad);
        float sinPitch = (float) Math.sin(pitchRad);
        
        Vector3f forward = new Vector3f(
            sinYaw * cosPitch,
            -sinPitch,
            -cosYaw * cosPitch
        );
        
        Vector3f up = new Vector3f(0, 1, 0);
        Vector3f right = new Vector3f();
        forward.cross(up, right);
        right.normalize();
        Vector3f finalUp = new Vector3f();
        right.cross(forward, finalUp);
        finalUp.normalize();
        
        Vector3f center = new Vector3f(position).add(forward);
        viewMatrix.lookAt(position, center, finalUp);
    }

    public void setPosition(Vector3f position) {
        this.position.set(position);
    }

    public void setPitch(float pitch) {
        this.pitch = Math.max(-90, Math.min(90, pitch));
    }

    public void setProjectionMatrix(Matrix4f projectionMatrix) {
        this.projectionMatrix.set(projectionMatrix);
    }
}

