package com.ksptool.mycraft.rendering;

import com.ksptool.mycraft.entity.BoundingBox;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public class Frustum {
    private Vector4f[] planes = new Vector4f[6];
    
    public Frustum() {
        for (int i = 0; i < 6; i++) {
            planes[i] = new Vector4f();
        }
    }
    
    public void update(Matrix4f projectionMatrix, Matrix4f viewMatrix) {
        Matrix4f viewProjection = new Matrix4f();
        projectionMatrix.mul(viewMatrix, viewProjection);
        
        float[] m = new float[16];
        viewProjection.get(m);
        
        planes[0].set(m[3] + m[0], m[7] + m[4], m[11] + m[8], m[15] + m[12]).normalize3();
        planes[1].set(m[3] - m[0], m[7] - m[4], m[11] - m[8], m[15] - m[12]).normalize3();
        planes[2].set(m[3] + m[1], m[7] + m[5], m[11] + m[9], m[15] + m[13]).normalize3();
        planes[3].set(m[3] - m[1], m[7] - m[5], m[11] - m[9], m[15] - m[13]).normalize3();
        planes[4].set(m[3] + m[2], m[7] + m[6], m[11] + m[10], m[15] + m[14]).normalize3();
        planes[5].set(m[3] - m[2], m[7] - m[6], m[11] - m[10], m[15] - m[14]).normalize3();
    }
    
    public boolean intersects(BoundingBox box) {
        for (int i = 0; i < 6; i++) {
            Vector4f plane = planes[i];
            float a = plane.x;
            float b = plane.y;
            float c = plane.z;
            float d = plane.w;
            
            float nx = a >= 0 ? box.getMaxX() : box.getMinX();
            float ny = b >= 0 ? box.getMaxY() : box.getMinY();
            float nz = c >= 0 ? box.getMaxZ() : box.getMinZ();
            
            float distance = a * nx + b * ny + c * nz + d;
            if (distance < 0) {
                return false;
            }
        }
        return true;
    }
}

