package com.zurrtum.create.infrastructure.polymer;

import org.joml.Matrix3f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class PolymerBedrockPartPose {
    private PolymerBedrockPartPose() {}

    static final float RIGID_TOLERANCE = 1e-3f;

    public record Pose(double x, double y, double z, Quaternionf rotation) {}

    public static Pose of(Matrix4fc m, int bx, int by, int bz) {
        Matrix3f linear = m.get3x3(new Matrix3f());
        Vector3f column = new Vector3f();
        for (int i = 0; i < 3; i++)
            if (Math.abs(linear.getColumn(i, column).length() - 1) > RIGID_TOLERANCE) return null;
        if (Math.abs(linear.determinant() - 1) > RIGID_TOLERANCE) return null;
        Vector3f centre = m.transformPosition(new Vector3f(0.5f, 0.5f, 0.5f));
        Quaternionf rotation = linear.getNormalizedRotation(new Quaternionf());
        return new Pose(bx + (double) centre.x, by + (double) centre.y, bz + (double) centre.z, rotation);
    }
}
