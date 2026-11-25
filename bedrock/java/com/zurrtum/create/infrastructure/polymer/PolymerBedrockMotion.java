package com.zurrtum.create.infrastructure.polymer;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public final class PolymerBedrockMotion {
    public static final String SNAPSHOT_KEY = "polymerpatch.meshFrameSnapshot.v2";
    public static final String HOOK_SUFFIX = ".frame.v2";
    public static final double HOST_REBASE_DISTANCE_SQUARED = 64;

    private PolymerBedrockMotion() {}

    public record Pose(Vec3 pivot, Quaternionf rotation) {
        public Pose {
            rotation = new Quaternionf(rotation).normalize();
        }
    }

    public record Frame(Pose previous, Pose current, int sequence, boolean snap) {
        /** JDK-only bridge wire contract (MeshFrameData): IDs stay integers, floats retain every fraction bit. */
        public int[] data(int entityId, PolymerBedrockMeshes.Model model, BlockPos local) {
            var offset = current.rotation().transform(new org.joml.Vector3f(local.getX(), local.getY(), local.getZ()));
            Vec3 pivot = current.pivot();
            return data(
                    entityId, model, local, new Vec3(pivot.x + offset.x, pivot.y + offset.y - 0.5, pivot.z + offset.z));
        }

        public int[] data(int entityId, PolymerBedrockMeshes.Model model, BlockPos local, Vec3 hostOrigin) {
            Quaternionf a = previous.rotation(), b = current.rotation();
            Vec3 p = previous.pivot(), q = current.pivot();
            float[] values = {
                a.x,
                a.y,
                a.z,
                a.w,
                b.x,
                b.y,
                b.z,
                b.w,
                (float) p.x,
                (float) p.y,
                (float) p.z,
                (float) q.x,
                (float) q.y,
                (float) q.z,
                local.getX(),
                local.getY(),
                local.getZ(),
                (float) hostOrigin.x,
                (float) hostOrigin.y,
                (float) hostOrigin.z
            };
            int[] data = new int[25];
            data[0] = entityId;
            data[1] = model.geometry();
            data[2] = model.material();
            data[3] = sequence;
            data[4] = snap ? 1 : 0;
            for (int i = 0; i < values.length; i++) data[5 + i] = Float.floatToIntBits(values[i]);
            return data;
        }
    }

    public static final class State {
        private Frame frame;

        public Frame sample(Vec3 pivot, Quaternionf rotation) {
            Pose next = new Pose(pivot, rotation);
            if (frame == null) return frame = new Frame(next, next, 0, true);
            Pose old = frame.current();
            Quaternionf a = old.rotation(), b = next.rotation();
            double delta = distance(a, b, 1), opposite = distance(a, b, -1);
            if (old.pivot().distanceToSqr(pivot) < 1e-12 && Math.min(delta, opposite) < 1e-12) return frame;
            if (old.rotation().dot(next.rotation()) < 0) next.rotation().mul(-1);
            boolean snap = old.pivot().distanceToSqr(pivot) > 256;
            frame = new Frame(snap ? next : old, next, (frame.sequence() + 1) & 1048575, snap);
            return frame;
        }

        private static double distance(Quaternionf a, Quaternionf b, int sign) {
            double x = a.x - sign * b.x, y = a.y - sign * b.y, z = a.z - sign * b.z, w = a.w - sign * b.w;
            return x * x + y * y + z * z + w * w;
        }
    }
}
