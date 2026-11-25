package com.zurrtum.create.infrastructure.polymer.contraption;

import com.zurrtum.create.content.contraptions.AbstractContraptionEntity;
import com.zurrtum.create.infrastructure.player.FakePlayerEntity;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class PolymerContraptionPhysics {
    private static final Map<UUID, Rider> RIDERS = new HashMap<>();
    private static long tick;

    public record Transform(Vec3 origin, Vec3 x, Vec3 y, Vec3 z) {
        static Transform sample(AbstractContraptionEntity entity) {
            Vec3 origin = entity.toGlobalVector(Vec3.ZERO, 1);
            return new Transform(
                    origin,
                    entity.toGlobalVector(new Vec3(1, 0, 0), 1).subtract(origin),
                    entity.toGlobalVector(new Vec3(0, 1, 0), 1).subtract(origin),
                    entity.toGlobalVector(new Vec3(0, 0, 1), 1).subtract(origin));
        }

        Vec3 toLocal(Vec3 point) {
            Vec3 relative = point.subtract(origin);
            Vec3 yz = y.cross(z);
            double determinant = x.dot(yz);
            return new Vec3(
                    relative.dot(yz) / determinant,
                    relative.dot(z.cross(x)) / determinant,
                    relative.dot(x.cross(y)) / determinant);
        }

        Vec3 toWorld(Vec3 point) {
            return origin.add(x.scale(point.x)).add(y.scale(point.y)).add(z.scale(point.z));
        }

        Vec3 motionFrom(Transform previous, Vec3 point) {
            return toWorld(previous.toLocal(point)).subtract(point);
        }
    }

    record Cell(AABB bounds, double previousTop) {}

    record Surface(
            AbstractContraptionEntity entity,
            Transform previous,
            Transform current,
            List<Cell> cells,
            Collection<ServerGamePacketListenerImpl> viewers) {}

    record Contact(Surface surface, Cell cell, double gap) {}

    private static final class Rider {
        AbstractContraptionEntity owner;
        Vec3 previousCarry = Vec3.ZERO;
        double previousDrag = .6f * .91f;
        long jumpUntil;
        boolean jumpHeld;
    }

    static void tick(MinecraftServer server, List<Surface> surfaces) {
        tick++;
        RIDERS.keySet().removeIf(id -> NativePresentationPolicy.presentationPlayer(server.getPlayerList(), id) == null);
        for (ServerPlayer player : NativePresentationPolicy.presentationPlayers(server.getPlayerList())) {
            UUID id = player.getUUID();
            if (!eligible(player)) {
                RIDERS.remove(id);
                continue;
            }
            Rider rider = RIDERS.computeIfAbsent(id, ignored -> new Rider());
            if (tick < rider.jumpUntil) continue;
            Contact contact = contact(player, surfaces, rider.owner);
            if (contact == null) {
                RIDERS.remove(id);
                continue;
            }
            if (rider.owner != contact.surface.entity) {
                rider.previousCarry = Vec3.ZERO;
                rider.owner = contact.surface.entity;
            }
            carry(player, rider, contact);
        }
    }

    private static boolean eligible(ServerPlayer player) {
        if (PolymerContraptionLab.controls(player) || PolymerShiftCarry.manages(player)) return false;
        return !(player instanceof FakePlayerEntity)
                && player.isAlive()
                && !player.isSpectator()
                && !player.isPassenger()
                && !player.getAbilities().flying
                && !player.hurtMarked
                && !player.level().tickRateManager().isEntityFrozen(player)
                && player.connection.awaitingPositionFromClient == null;
    }

    static Contact contact(ServerPlayer player, List<Surface> surfaces, AbstractContraptionEntity owner) {
        Contact best = null;
        AABB feet = player.getBoundingBox();
        for (Surface surface : surfaces) {
            if (surface.previous == null
                    || surface.entity.level() != player.level()
                    || surface.entity.isRemoved()
                    || !surface.viewers.contains(player.connection)
                    || surface.entity.level().tickRateManager().isEntityFrozen(surface.entity)) continue;
            for (Cell cell : surface.cells) {
                double gap = player.getY() - cell.bounds.maxY;
                double tolerance = Math.min(
                        .3,
                        Math.max(
                                surface.entity == owner ? .10 : .06,
                                Math.abs(cell.bounds.maxY - cell.previousTop) + .04));
                if (!supports(feet, cell.bounds, tolerance)) continue;
                if (best == null
                        || Math.abs(gap) < Math.abs(best.gap) - 1e-6
                        || Math.abs(Math.abs(gap) - Math.abs(best.gap)) < 1e-6
                                && surface.entity == owner
                                && best.surface.entity != owner) {
                    best = new Contact(surface, cell, gap);
                }
            }
        }
        return best;
    }

    static boolean supports(AABB feet, AABB floor, double tolerance) {
        double gap = feet.minY - floor.maxY;
        return gap >= -tolerance
                && gap <= .14
                && feet.maxX > floor.minX + 1e-5
                && feet.minX < floor.maxX - 1e-5
                && feet.maxZ > floor.minZ + 1e-5
                && feet.minZ < floor.maxZ - 1e-5;
    }

    private static void carry(ServerPlayer player, Rider rider, Contact contact) {
        Surface surface = contact.surface;
        Vec3 motion = surface.current.motionFrom(surface.previous, player.position());
        if (!finite(motion) || motion.lengthSqr() > .8 * .8) {
            RIDERS.remove(player.getUUID());
            return;
        }
        boolean jump = player.getLastClientInput().jump();
        boolean hybrid = Math.abs(motion.y) > 1e-5 || Math.abs(surface.current.y.y - 1) > 1e-5;
        if ((!hybrid && (jump || player.getKnownMovement().y > .10))
                || hybrid && player.getKnownMovement().y - motion.y > .12) {
            rider.jumpUntil = tick + 8;
            rider.previousCarry = Vec3.ZERO;
            rider.jumpHeld = jump;
            return;
        }
        boolean grounded = player.onGround() || contact.gap <= .02;
        Vec3 current = player.getDeltaMovement();
        if (grounded && !hybrid && current.y < 0) {
            current = new Vec3(current.x, 0, current.z);
        }
        Vec3 increment = carryIncrement(rider.previousCarry, motion, rider.previousDrag);
        boolean preserveY = !hybrid || jump && !rider.jumpHeld;
        double supportY = hybrid
                ? contact.cell.bounds.maxY - contact.cell.previousTop + Math.clamp(-contact.gap * .2, -.02, .02)
                : current.y;
        Vec3 next = new Vec3(current.x + increment.x, preserveY ? current.y : supportY, current.z + increment.z);
        if (grounded) player.setOnGround(true);
        player.setDeltaMovement(next);
        player.applyPostImpulseGraceTime(10);
        NativePresentationPolicy.sendPresentation(
                player, carryPacket(player.getId(), increment, supportY, preserveY, grounded));
        rider.previousCarry = new Vec3(motion.x, 0, motion.z);
        rider.previousDrag = grounded ? .6f * .91f : .91f;
        player.connection.resetFlyingTicks();
        surface.entity.registerColliding(player);
        if (player.fallDistance > 0) {
            player.causeFallDamage(
                    player.fallDistance, 1, player.damageSources().fall());
            player.fallDistance = 0;
        }
        rider.jumpHeld = jump;
    }

    static Vec3 carryIncrement(Vec3 previous, Vec3 carry, double drag) {
        return new Vec3(carry.x - previous.x * drag, 0, carry.z - previous.z * drag);
    }

    public static void forgetPlayer(ServerPlayer player) {
        RIDERS.remove(player.getUUID());
    }

    static ClientboundTeleportEntityPacket carryPacket(
            int id, Vec3 increment, double supportY, boolean preserveY, boolean grounded) {
        EnumSet<Relative> flags = EnumSet.of(
                Relative.X, Relative.Y, Relative.Z, Relative.Y_ROT, Relative.X_ROT, Relative.DELTA_X, Relative.DELTA_Z);
        if (preserveY) flags.add(Relative.DELTA_Y);
        return new ClientboundTeleportEntityPacket(
                id,
                new PositionMoveRotation(Vec3.ZERO, new Vec3(increment.x, preserveY ? 0 : supportY, increment.z), 0, 0),
                flags,
                grounded);
    }

    private static boolean finite(Vec3 point) {
        return Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }

    static void remove(AbstractContraptionEntity entity) {
        RIDERS.values().removeIf(rider -> rider.owner == entity);
    }

    static void clear() {
        RIDERS.clear();
        tick = 0;
    }
}
