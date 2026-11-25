package com.zurrtum.create.infrastructure.polymer.contraption;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class PolymerShiftGeometry {
    private static final Vec3 X = new Vec3(1, 0, 0), Y = new Vec3(0, 1, 0), Z = new Vec3(0, 0, 1);
    private static final double EPSILON = 1e-7;

    private PolymerShiftGeometry() {}

    static boolean valid(PolymerContraptionPhysics.Transform pose) {
        if (pose == null || !finite(pose.origin()) || !finite(pose.x()) || !finite(pose.y()) || !finite(pose.z()))
            return false;
        double determinant = pose.x().dot(pose.y().cross(pose.z()));
        return determinant > .9 && determinant < 1.1;
    }

    static boolean finite(Vec3 p) {
        return Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z);
    }

    static boolean near(AABB player, AABB object, double radius) {
        return distanceSqr(player, object) <= radius * radius;
    }

    static double distanceSqr(AABB a, AABB b) {
        double x = Math.max(0, Math.max(a.minX - b.maxX, b.minX - a.maxX));
        double y = Math.max(0, Math.max(a.minY - b.maxY, b.minY - a.maxY));
        double z = Math.max(0, Math.max(a.minZ - b.maxZ, b.minZ - a.maxZ));
        return x * x + y * y + z * z;
    }

    static List<AABB> proxies(AABB box) {
        double size = Math.min(1, Math.min(box.getXsize(), Math.min(box.getYsize(), box.getZsize())));
        if (size < 1.0 / 16) return List.of();
        int nx = (int) Math.ceil(box.getXsize() / size - EPSILON);
        int ny = (int) Math.ceil(box.getYsize() / size - EPSILON);
        int nz = (int) Math.ceil(box.getZsize() / size - EPSILON);
        if ((long) nx * ny * nz > 64) return List.of();
        List<AABB> cubes = new ArrayList<>();
        for (int x = 0; x < nx; x++)
            for (int y = 0; y < ny; y++)
                for (int z = 0; z < nz; z++) {
                    double px = Math.min(box.minX + x * size, box.maxX - size);
                    double py = Math.min(box.minY + y * size, box.maxY - size);
                    double pz = Math.min(box.minZ + z * size, box.maxZ - size);
                    cubes.add(new AABB(px, py, pz, px + size, py + size, pz + size));
                }
        return cubes;
    }

    static AABB bounds(AABB box, PolymerContraptionPhysics.Transform pose) {
        Vec3 centre = pose.toWorld(box.getCenter());
        double hx = box.getXsize() / 2, hy = box.getYsize() / 2, hz = box.getZsize() / 2;
        double x = Math.abs(pose.x().x) * hx + Math.abs(pose.y().x) * hy + Math.abs(pose.z().x) * hz;
        double y = Math.abs(pose.x().y) * hx + Math.abs(pose.y().y) * hy + Math.abs(pose.z().y) * hz;
        double z = Math.abs(pose.x().z) * hx + Math.abs(pose.y().z) * hy + Math.abs(pose.z().z) * hz;
        return new AABB(centre.x - x, centre.y - y, centre.z - z, centre.x + x, centre.y + y, centre.z + z);
    }

    static AABB sweptBounds(
            AABB box, PolymerContraptionPhysics.Transform from, PolymerContraptionPhysics.Transform to) {
        double dot = Math.min(1, Math.abs(rotation(from).dot(rotation(to))));
        double angle = 2 * Math.acos(dot);
        int segments = Math.max(1, (int) Math.ceil(angle / Math.toRadians(5)));
        AABB swept = bounds(box, from).minmax(bounds(box, to));
        for (int k = 1; k < segments; k++) swept = swept.minmax(bounds(box, at(from, to, k / (double) segments)));
        double radius = box.getCenter().length()
                + Math.sqrt(box.getXsize() * box.getXsize()
                                + box.getYsize() * box.getYsize()
                                + box.getZsize() * box.getZsize())
                        / 2;
        return swept.inflate(radius * (1 - Math.cos(angle / (2 * segments))) + 1e-5);
    }

    private static Quaternionf rotation(PolymerContraptionPhysics.Transform p) {
        return new Matrix3f()
                .setColumn(0, vector(p.x()))
                .setColumn(1, vector(p.y()))
                .setColumn(2, vector(p.z()))
                .getNormalizedRotation(new Quaternionf());
    }

    private static Vector3f vector(Vec3 v) {
        return new Vector3f((float) v.x, (float) v.y, (float) v.z);
    }

    private static Vec3 rotated(Quaternionf q, float x, float y, float z) {
        Vector3f v = q.transform(new Vector3f(x, y, z));
        return new Vec3(v.x, v.y, v.z);
    }

    private static PolymerContraptionPhysics.Transform at(
            PolymerContraptionPhysics.Transform a, PolymerContraptionPhysics.Transform b, double t) {
        Quaternionf q = rotation(a).slerp(rotation(b), (float) t);
        return new PolymerContraptionPhysics.Transform(
                a.origin().lerp(b.origin(), t), rotated(q, 1, 0, 0), rotated(q, 0, 1, 0), rotated(q, 0, 0, 1));
    }

    static Vec3 sweep(
            AABB playerFrom,
            AABB playerTo,
            AABB local,
            PolymerContraptionPhysics.Transform from,
            PolymerContraptionPhysics.Transform to) {
        double dot = Math.min(1, Math.abs(rotation(from).dot(rotation(to))));
        int segments = Math.max(1, Math.min(36, (int) Math.ceil(2 * Math.acos(dot) / Math.toRadians(5))));
        Vec3 correction = Vec3.ZERO;
        for (int k = 0; k < segments; k++) {
            double t0 = k / (double) segments, t1 = (k + 1) / (double) segments;
            var a = at(from, to, t0);
            var b = at(from, to, t1);
            var middle = at(from, to, (t0 + t1) / 2);
            AABB p0 = lerp(playerFrom, playerTo, t0).move(correction);
            AABB p1 = lerp(playerFrom, playerTo, t1).move(correction);
            Vec3 push = translationSweep(p0, p1, local, a, b, middle);
            correction = correction.add(push);
        }
        return correction;
    }

    private static AABB lerp(AABB a, AABB b, double t) {
        return new AABB(
                a.minX + (b.minX - a.minX) * t,
                a.minY + (b.minY - a.minY) * t,
                a.minZ + (b.minZ - a.minZ) * t,
                a.maxX + (b.maxX - a.maxX) * t,
                a.maxY + (b.maxY - a.maxY) * t,
                a.maxZ + (b.maxZ - a.maxZ) * t);
    }

    private static Vec3 translationSweep(
            AABB p0,
            AABB p1,
            AABB box,
            PolymerContraptionPhysics.Transform from,
            PolymerContraptionPhysics.Transform to,
            PolymerContraptionPhysics.Transform orientation) {
        Vec3 c0 = from.toWorld(box.getCenter()), c1 = to.toWorld(box.getCenter());
        Vec3 relative = p0.getCenter().subtract(c0);
        Vec3 movement = p1.getCenter().subtract(p0.getCenter()).subtract(c1.subtract(c0));
        Vec3[] basis = {
            orientation.x().normalize(),
            orientation.y().normalize(),
            orientation.z().normalize()
        };
        List<Vec3> axes = new ArrayList<>(15);
        axes.addAll(List.of(X, Y, Z, basis[0], basis[1], basis[2]));
        for (Vec3 axis : List.of(X, Y, Z))
            for (Vec3 v : basis)
                if (axis.cross(v).lengthSqr() > 1e-10) axes.add(axis.cross(v).normalize());
        double entry = Double.NEGATIVE_INFINITY, exit = Double.POSITIVE_INFINITY, depth = Double.POSITIVE_INFINITY;
        Vec3 entryNormal = Vec3.ZERO, overlapNormal = Vec3.ZERO;
        boolean overlap = true;
        for (Vec3 axis : axes) {
            double radius = (Math.abs(axis.x) * Math.max(p0.getXsize(), p1.getXsize())
                                    + Math.abs(axis.y) * Math.max(p0.getYsize(), p1.getYsize())
                                    + Math.abs(axis.z) * Math.max(p0.getZsize(), p1.getZsize()))
                            / 2
                    + (Math.abs(axis.dot(basis[0])) * box.getXsize()
                                    + Math.abs(axis.dot(basis[1])) * box.getYsize()
                                    + Math.abs(axis.dot(basis[2])) * box.getZsize())
                            / 2;
            double position = relative.dot(axis),
                    velocity = movement.dot(axis),
                    penetration = radius - Math.abs(position);
            if (penetration <= EPSILON) overlap = false;
            if (penetration < depth) {
                depth = penetration;
                overlapNormal = axis.scale(position >= 0 ? 1 : -1);
            }
            if (Math.abs(velocity) < 1e-10) {
                if (Math.abs(position) >= radius - EPSILON) return Vec3.ZERO;
                continue;
            }
            double first = (-radius - position) / velocity, last = (radius - position) / velocity;
            Vec3 normal = axis.scale(velocity > 0 ? -1 : 1);
            if (first > last) {
                double swap = first;
                first = last;
                last = swap;
            }
            if (first > entry) {
                entry = first;
                entryNormal = normal;
            }
            exit = Math.min(exit, last);
            if (entry > exit) return Vec3.ZERO;
        }
        if (overlap) {
            double remaining = depth - movement.dot(overlapNormal);
            return remaining > EPSILON ? overlapNormal.scale(remaining + 1e-5) : Vec3.ZERO;
        }
        if (entry < 0 || entry > 1 || exit < 0 || entryNormal.lengthSqr() < .5) return Vec3.ZERO;
        double remaining = -movement.dot(entryNormal) * (1 - entry);
        return remaining > EPSILON ? entryNormal.scale(remaining + 1e-5) : Vec3.ZERO;
    }
}
