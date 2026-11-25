package com.zurrtum.create.infrastructure.polymer.contraption;

import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class PolymerBedrockRiderDeck {
    public static final String POSE_KEY = "polymerpatch.bedrockCarryDeckPose.v1";
    public static final String HOSTS_KEY = "polymerpatch.bedrockCarryDeckHosts.v1";
    private static final Vec3 CENTRE = new Vec3(.5, .5, .5);

    private PolymerBedrockRiderDeck() {}

    static void install(Function<UUID, int[]> hosts) {
        System.getProperties().put(POSE_KEY, (BiFunction<UUID, Integer, Object[]>) PolymerBedrockRiderDeck::pose);
        System.getProperties().put(HOSTS_KEY, hosts);
    }

    private static Object[] pose(UUID player, Integer sourceId) {
        if (player == null || sourceId == null) return null;
        var frame = PolymerObserverFrameHistory.frame(player, sourceId);
        if (frame == null) return null;
        float[] pose = pose(frame.pose());
        return pose == null ? null : new Object[] {frame.owner(), pose};
    }

    static float[] pose(PolymerContraptionPhysics.Transform transform) {
        Vec3 pivot = transform.toWorld(CENTRE);
        Matrix3f basis = new Matrix3f()
                .setColumn(0, vector(transform.x()))
                .setColumn(1, vector(transform.y()))
                .setColumn(2, vector(transform.z()));
        Quaternionf q = basis.getNormalizedRotation(new Quaternionf());
        float[] out = {(float) pivot.x, (float) pivot.y, (float) pivot.z, q.x, q.y, q.z, q.w};
        for (float value : out) if (!Float.isFinite(value)) return null;
        return out;
    }

    private static Vector3f vector(Vec3 value) {
        return new Vector3f((float) value.x, (float) value.y, (float) value.z);
    }
}
