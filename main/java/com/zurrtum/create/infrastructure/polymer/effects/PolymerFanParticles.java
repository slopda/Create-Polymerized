package com.zurrtum.create.infrastructure.polymer.effects;

import com.zurrtum.create.content.kinetics.fan.AirCurrent;
import com.zurrtum.create.content.kinetics.fan.processing.FanProcessingType;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.TrailParticleOption;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public final class PolymerFanParticles {
    private PolymerFanParticles() {}

    public record Flight(Vec3 target, int duration) {}

    public static Flight flight(AirCurrent current, Vec3 start) {
        if (current.direction == null
                || !Float.isFinite(current.maxDistance)
                || current.maxDistance <= 0
                || !current.bounds.inflate(.25).contains(start)) return new Flight(start, 0);
        Vec3 axis = current.direction.getUnitVec3();
        Vec3 origin = Vec3.atCenterOf(current.source.getAirCurrentPos());
        Vec3 position = start;
        for (int age = 1; age <= 40; age++) {
            double distance = position.subtract(origin).multiply(axis).length() - .5;
            if (distance > current.maxDistance + 1 || distance < -.25) return new Flight(position, age - 1);
            Vec3 motion = axis.scale((current.pushing ? 1 : -1) * (current.maxDistance - (distance - 1)) / 16);
            Vec3 next = position.add(motion);
            if (!current.bounds.inflate(.25).contains(next)) return new Flight(position, age - 1);
            position = next;
        }
        return new Flight(position, 40);
    }

    public static void tick(AirCurrent current) {
        if (!(current.source.getAirCurrentWorld() instanceof ServerLevel world)
                || current.source.isSourceRemoved()
                || current.direction == null
                || current.maxDistance <= 0
                || current.source.getSpeed() == 0
                || world.getGameTime() % 4 != 0) return;
        var viewers = world.players().stream()
                .filter(p -> !PolymerNativeClients.isNative(p)
                        && p.distanceToSqr(Vec3.atCenterOf(current.source.getAirCurrentPos())) < 32 * 32)
                .toList();
        if (viewers.isEmpty()) return;
        Vec3 axis = current.direction.getUnitVec3();
        Vec3 origin = Vec3.atCenterOf(current.source.getAirCurrentPos());
        double offset = current.pushing ? .5 : current.maxDistance + .5;
        for (int i = 0; i < 3; i++) {
            Vec3 jitter = new Vec3(
                            world.getRandom().nextDouble() - .5,
                            world.getRandom().nextDouble() - .5,
                            world.getRandom().nextDouble() - .5)
                    .scale(.36)
                    .multiply(1 - Math.abs(axis.x), 1 - Math.abs(axis.y), 1 - Math.abs(axis.z));
            Vec3 start = origin.add(axis.scale(offset)).add(jitter);
            Flight flight = flight(current, start);
            if (flight.duration() <= 0 || flight.target().distanceToSqr(start) < 1e-8) continue;
            int[] color = {0xEEEEEE};
            double distance = start.subtract(origin).multiply(axis).length() - .5;
            FanProcessingType type = current.getTypeAt((float) distance);
            if (type == null) {
                Vec3 air = start.add(
                        flight.target().subtract(start).scale(world.getRandom().nextDouble()));
                Vec3 motion = axis.scale((current.pushing ? 1 : -1) * Math.min(.25, (current.maxDistance + 1) / 16));
                for (var viewer : viewers)
                    world.sendParticles(
                            viewer,
                            ParticleTypes.WHITE_SMOKE,
                            false,
                            false,
                            air.x,
                            air.y,
                            air.z,
                            0,
                            motion.x,
                            motion.y,
                            motion.z,
                            1);
                continue;
            }
            type.morphAirFlow(
                    new FanProcessingType.AirFlowParticleAccess() {
                        @Override
                        public void setColor(int value) {
                            color[0] = value;
                        }

                        @Override
                        public void setAlpha(float value) {}

                        @Override
                        public void spawnExtraParticle(ParticleOptions particle, float multiplier) {
                            Vec3 motion = axis.scale((current.pushing ? 1 : -1) * .125 * multiplier);
                            for (var viewer : viewers)
                                world.sendParticles(
                                        viewer, particle, false, false, start.x, start.y, start.z, 0, motion.x,
                                        motion.y, motion.z, 1);
                        }
                    },
                    world.getRandom());
            var particle = new TrailParticleOption(flight.target(), color[0], flight.duration());
            for (var viewer : viewers)
                world.sendParticles(viewer, particle, false, false, start.x, start.y, start.z, 1, 0, 0, 0, 0);
        }
    }
}
