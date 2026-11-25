package com.zurrtum.create.infrastructure.polymer.effects;

import com.google.common.collect.Streams;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class PolymerSoulPulse {
    private PolymerSoulPulse() {}

    private static final int MAX_DISTANCE = 11, WAITING_TICKS = 100, TICKS_PER_LAYER = 6;
    private static final List<List<BlockPos>> LAYERS = genLayers();
    private static final Map<ServerLevel, Handler> HANDLERS = new WeakHashMap<>();
    private static boolean registered;

    private static final class Pulse {
        final BlockPos pos;
        final int distance;
        final List<BlockPos> added;
        int ticks;

        Pulse(BlockPos pos, int distance, boolean canOverlap) {
            this.pos = pos;
            this.distance = distance;
            ticks = TICKS_PER_LAYER * distance;
            added = canOverlap ? null : new ArrayList<>();
        }

        boolean finished() {
            return ticks <= -WAITING_TICKS;
        }

        List<BlockPos> tick(ServerLevel level) {
            if (finished()) return null;
            ticks--;
            if (ticks < 0 || ticks % TICKS_PER_LAYER != 0) return null;
            List<BlockPos> spawns = spawns(level);
            while (spawns.isEmpty() && ticks > 0) {
                ticks -= TICKS_PER_LAYER;
                spawns.addAll(spawns(level));
            }
            return spawns;
        }

        private List<BlockPos> spawns(ServerLevel level) {
            int layer = distance - ticks / TICKS_PER_LAYER - 1;
            List<BlockPos> result = new ArrayList<>();
            if (layer < 0 || layer >= MAX_DISTANCE) return result;
            for (BlockPos offset : LAYERS.get(layer)) {
                BlockPos at = offset.offset(pos);
                if (canSpawnSoulAt(level, at)) result.add(at);
            }
            return result;
        }
    }

    private static final class Handler {
        final List<Pulse> pulses = new LinkedList<>();
        final Set<BlockPos> occupied = new HashSet<>();

        void tick(ServerLevel level) {
            Iterator<Pulse> iterator = pulses.iterator();
            while (iterator.hasNext()) {
                Pulse pulse = iterator.next();
                List<BlockPos> spawns = pulse.tick(level);
                if (spawns != null) {
                    if (pulse.added == null) {
                        for (BlockPos pos : spawns) spawn(level, pos);
                        if (pulse.finished()) iterator.remove();
                    } else if (pulse.finished()) {
                        for (BlockPos pos : spawns) if (!occupied.contains(pos)) spawn(level, pos);
                        pulse.added.forEach(occupied::remove);
                        iterator.remove();
                    } else {
                        for (BlockPos pos : spawns)
                            if (occupied.add(pos)) {
                                spawn(level, pos);
                                pulse.added.add(pos);
                            }
                    }
                } else if (pulse.finished()) {
                    if (pulse.added != null) pulse.added.forEach(occupied::remove);
                    iterator.remove();
                }
            }
        }
    }

    public static void add(ServerLevel level, BlockPos pos, int distance, boolean canOverlap) {
        if (!registered) {
            registered = true;
            ServerTickEvents.END_LEVEL_TICK.register(world -> {
                Handler handler = HANDLERS.get(world);
                if (handler != null) handler.tick(world);
            });
            ServerLifecycleEvents.SERVER_STOPPING.register(server -> HANDLERS.clear());
        }
        Handler handler = HANDLERS.computeIfAbsent(level, k -> new Handler());
        if (handler.pulses.size() < 256) handler.pulses.add(new Pulse(pos, distance, canOverlap));
    }

    private static void spawn(ServerLevel level, BlockPos at) {
        if (level.getBrightness(LightLayer.BLOCK, at) >= 1) return;
        PolymerVanillaEffects.sourceParticles(
                level,
                ParticleTypes.SOUL,
                true,
                false,
                at.getX() + .5,
                at.getY() + .5,
                at.getZ() + .5,
                0,
                0,
                0.02,
                0,
                1);
        PolymerVanillaEffects.sourceParticles(
                level,
                ParticleTypes.SCULK_SOUL,
                false,
                false,
                at.getX() + .5,
                at.getY() + .05,
                at.getZ() + .5,
                0,
                0,
                0,
                0,
                1);
    }

    private static boolean canSpawnSoulAt(ServerLevel level, BlockPos at) {
        if (!level.isLoaded(at)) return false;
        double w2 = 0.1;
        return SpawnPlacementTypes.ON_GROUND.isSpawnPositionOk(level, at, EntityTypes.ZOMBIE)
                && Streams.stream(level.getBlockCollisions(
                                null,
                                new AABB(
                                        at.getX() + .5 - w2,
                                        at.getY(),
                                        at.getZ() + .5 - w2,
                                        at.getX() + .5 + w2,
                                        at.getY() + .75,
                                        at.getZ() + .5 + w2)))
                        .allMatch(VoxelShape::isEmpty);
    }

    private static List<List<BlockPos>> genLayers() {
        List<List<BlockPos>> layers = new ArrayList<>();
        for (int i = 0; i < MAX_DISTANCE; i++) layers.add(new ArrayList<>());
        for (int x = 0; x < MAX_DISTANCE; x++)
            for (int y = 0; y < MAX_DISTANCE; y++)
                for (int z = 0; z < MAX_DISTANCE; z++) {
                    BlockPos candidate = new BlockPos(x, y, z);
                    int dist = (int) Math.round(Math.sqrt(candidate.distSqr(BlockPos.ZERO)));
                    if (dist > MAX_DISTANCE) continue;
                    if (dist <= 0) dist = 1;
                    List<BlockPos> layer = layers.get(dist - 1);
                    int start = layer.size(), end = start + 1;
                    layer.add(candidate);
                    if (candidate.getX() != 0) {
                        layer.add(new BlockPos(-candidate.getX(), candidate.getY(), candidate.getZ()));
                        end += 1;
                    }
                    if (candidate.getY() != 0) {
                        for (int i = start; i < end; i++) {
                            BlockPos prev = layer.get(i);
                            layer.add(new BlockPos(prev.getX(), -prev.getY(), prev.getZ()));
                        }
                        end += end - start;
                    }
                    if (candidate.getZ() != 0) {
                        for (int i = start; i < end; i++) {
                            BlockPos prev = layer.get(i);
                            layer.add(new BlockPos(prev.getX(), prev.getY(), -prev.getZ()));
                        }
                    }
                }
        return layers;
    }
}
