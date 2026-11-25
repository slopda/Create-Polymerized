package com.zurrtum.create.content.trains.entity;

import com.zurrtum.create.catnip.math.AngleHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class PolymerBogeyParticlePose {
    public record Angles(float yaw, float pitch) {}

    private PolymerBogeyParticlePose() {}

    public static Angles sample(CarriageBogey bogey, CarriageContraptionEntity entity) {
        TravellingPoint a = bogey.points.getFirst(), b = bogey.points.getSecond();
        if (a.edge == null || bogey.carriage.train.derailed) return new Angles(90 - entity.yaw + bogey.derailAngle, 0);
        if (b.edge == null
                || a.edge.isInterDimensional()
                || b.edge.isInterDimensional()
                || a.node1 == null
                || b.node1 == null
                || !a.node1.getLocation().dimension.equals(b.node1.getLocation().dimension)
                || !entity.level().dimension().equals(a.node1.getLocation().dimension))
            return new Angles(90 - entity.yaw, 0);
        boolean upsideDown = bogey.isUpsideDown();
        Vec3 leading = new TravellingPoint(a.node1, a.node2, a.edge, a.position, upsideDown)
                .getPosition(bogey.carriage.train.graph);
        Vec3 trailing = new TravellingPoint(b.node1, b.node2, b.edge, b.position, upsideDown)
                .getPosition(bogey.carriage.train.graph);
        Vec3 diff = leading.subtract(trailing);
        float yaw = -(AngleHelper.deg(Mth.atan2(diff.z, diff.x)) + 90);
        float pitch = AngleHelper.deg(Math.atan2(diff.y, Math.sqrt(diff.x * diff.x + diff.z * diff.z)));
        return new Angles(yaw, pitch);
    }
}
