package com.ksptool.mycraft.world;

import org.joml.Vector3i;

/**
 * 射线检测结果类，存储射线检测的命中信息
 */
public class RaycastResult {

    //方块位置
    private Vector3i blockPosition;
    
    //面法线
    private Vector3i faceNormal;
    
    //是否命中
    private boolean hit;

    public RaycastResult() {
        this.blockPosition = new Vector3i();
        this.faceNormal = new Vector3i();
        this.hit = false;
    }

    public Vector3i getBlockPosition() {
        return blockPosition;
    }

    public void setBlockPosition(Vector3i blockPosition) {
        this.blockPosition = blockPosition;
    }

    public Vector3i getFaceNormal() {
        return faceNormal;
    }

    public void setFaceNormal(Vector3i faceNormal) {
        this.faceNormal = faceNormal;
    }

    public boolean isHit() {
        return hit;
    }

    public void setHit(boolean hit) {
        this.hit = hit;
    }
}

