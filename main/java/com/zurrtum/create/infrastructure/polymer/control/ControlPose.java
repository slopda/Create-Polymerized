package com.zurrtum.create.infrastructure.polymer.control;

import org.joml.Matrix4f;
import org.joml.Quaternionfc;

public final class ControlPose {
    public final Matrix4f matrix = new Matrix4f();

    public ControlPose translate(double x, double y, double z) {
        matrix.translate((float) x, (float) y, (float) z);
        return this;
    }

    public ControlPose scale(float x, float y, float z) {
        matrix.scale(x, y, z);
        return this;
    }

    public ControlPose rotateXDegrees(float degrees) {
        matrix.rotateX((float) Math.toRadians(degrees));
        return this;
    }

    public ControlPose rotateYDegrees(float degrees) {
        matrix.rotateY((float) Math.toRadians(degrees));
        return this;
    }

    public ControlPose rotateZDegrees(float degrees) {
        matrix.rotateZ((float) Math.toRadians(degrees));
        return this;
    }

    public void mulPose(Quaternionfc rotation) {
        matrix.rotate(rotation);
    }
}
