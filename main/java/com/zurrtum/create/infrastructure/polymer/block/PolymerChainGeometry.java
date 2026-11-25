package com.zurrtum.create.infrastructure.polymer.block;

import com.zurrtum.create.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

final class PolymerChainGeometry {
    record Point(float position, Vec3 world, double distanceSqr) {}

    static @Nullable Point loop(BlockPos anchor, Vec3 eye, Vec3 end) {
        Vec3 center = Vec3.atBottomCenterOf(anchor).add(0, .25, 0);
        AABB bounds = new AABB(center.add(-1, -.25, -1), center.add(1, .25, 1));
        Vec3 hit = bounds.clip(eye, end).orElse(null);
        if (hit == null) return null;
        Vec3 diff = center.subtract(hit);
        float angle = Math.round(((float) (Mth.RAD_TO_DEG * Math.atan2(diff.x, diff.z) + 540) % 360) / 45) * 45f;
        Vec3 point = center.add(VecHelper.rotate(new Vec3(0, 0, .875), angle, Axis.Y))
                .add(0, -.125, 0);
        return new Point(angle, point, hit.distanceToSqr(eye));
    }

    static @Nullable Point connection(BlockPos connection, Vec3 start, Vec3 finish, Vec3 eye, Vec3 end) {
        Vec3 diff = finish.subtract(start);
        double length = diff.length();
        if (length < 1e-8) return null;
        double yaw = Mth.RAD_TO_DEG * Math.atan2(diff.x, diff.z);
        double pitch = Mth.RAD_TO_DEG * Math.atan2(-diff.y, Math.hypot(diff.x, diff.z));
        Vec3 from = VecHelper.rotate(VecHelper.rotate(eye.subtract(start), -yaw, Axis.Y), -pitch, Axis.X);
        Vec3 to = VecHelper.rotate(VecHelper.rotate(end.subtract(start), -yaw, Axis.Y), -pitch, Axis.X);
        Vec3 local =
                new AABB(-.175, -.175, 0, .175, .175, length).clip(from, to).orElse(null);
        if (local == null) return null;
        Vec3 hit = VecHelper.rotate(VecHelper.rotate(local, pitch, Axis.X), yaw, Axis.Y)
                .add(start);
        int dots = (int) Math.round(Vec3.atLowerCornerOf(connection).length() - 3);
        double margin = length - dots;
        double selection = Math.min(length, hit.distanceTo(start));
        selection = Math.round(Mth.clamp(selection - margin, 0, length - margin * 2));
        float position = (float) (selection + margin + .025);
        Vec3 world = start.lerp(finish, Mth.clamp(position / length, 0, 1));
        return new Point(position, world, hit.distanceToSqr(eye));
    }

    static int phase(long time, float rpm, double totalLength, double start, double length) {
        return phase(time, rpm, totalLength, start, length, PolymerScrollAnimations.chainReady);
    }

    static int phase(long time, float rpm, double totalLength, double start, double length, boolean ready) {
        double uv = time * Math.abs((double) rpm) / 360 - .5 + totalLength - start - length;
        int phase = Math.floorMod((int) Math.floor((uv - Math.floor(uv)) * 16 + 1e-7), 16);
        return PolymerScrollAnimations.chainCode(rpm, -.5 + totalLength - start - length, phase, ready);
    }

    static final class Motion {
        Vec3 pos, motion = Vec3.ZERO, target;
        float yaw;
        long lastTick = Long.MIN_VALUE;

        void tick(long time, Vec3 world, float sourceYaw) {
            if (time == lastTick) return;
            lastTick = time;
            target = world.add(0, -9 / 16.0, 0);
            if (pos == null) pos = target;
            else {
                if (pos.distanceToSqr(world) > 2.25)
                    pos = world.add(pos.subtract(world).normalize().scale(1.5));
                motion = motion.add(0, -.25, 0)
                        .scale(.75)
                        .add(world.subtract(pos).scale(.25));
                pos = pos.add(motion);
            }
            yaw += Mth.wrapDegrees(sourceYaw - yaw) * .25f;
        }
    }
}
