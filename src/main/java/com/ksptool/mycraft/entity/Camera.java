package com.ksptool.mycraft.entity;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public class Camera {
    private Vector3f position;
    private float pitch;
    private float yaw;
    private float roll;
    private Matrix4f viewMatrix;
    private Matrix4f projectionMatrix;

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

    public Vector3f getPosition() {
        return position;
    }

    public void setPitch(float pitch) {
        this.pitch = Math.max(-90, Math.min(90, pitch));
    }

    public float getPitch() {
        return pitch;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public float getYaw() {
        return yaw;
    }

    public void setRoll(float roll) {
        this.roll = roll;
    }

    public float getRoll() {
        return roll;
    }

    public Matrix4f getViewMatrix() {
        return viewMatrix;
    }

    public Matrix4f getProjectionMatrix() {
        return projectionMatrix;
    }

    public void setProjectionMatrix(Matrix4f projectionMatrix) {
        this.projectionMatrix.set(projectionMatrix);
    }
}

