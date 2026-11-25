package com.zurrtum.create.infrastructure.polymer;

import eu.pb4.polymer.virtualentity.api.VirtualEntityUtils;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

public final class PolymerBedrockRotors {
    private PolymerBedrockRotors() {}

    public static boolean ENABLED = true;

    static final long MARKER = 0x7070726f746f7272L;

    /**
     * Rotor model names; a model's index here is its {@code polymerpatch:model} property value. The single source of
     * truth: the devtools parts census writes this list to {@code polymerpatch/bedrock_contract.json}, and the Bedrock
     * pack builder (RotorModels) refuses to build when its meshes are not in this order. Index 0 is the test cube.
     * Add new names at the end.
     */
    public static final List<String> MODEL_NAMES = List.of(
            "test",
            "cogwheel",
            "large_cogwheel",
            "shaft",
            "crushing_wheel",
            "flywheel",
            "water_wheel",
            "large_water_wheel",
            "large_water_wheel_extension",
            "part_shaft_half",
            "part_encased_shaft",
            "part_fan_propeller",
            "part_millstone",
            "part_drill_head",
            "part_hand_crank",
            "part_turntable",
            "part_pump_cog",
            "part_shaftless_cog",
            "part_arm_cog");

    public static int model(String name) {
        int index = MODEL_NAMES.indexOf(name);
        if (index < 0) throw new IllegalArgumentException("Unknown Bedrock rotor model " + name);
        return index;
    }

    public static final int MODEL_COGWHEEL = model("cogwheel"),
            MODEL_LARGE_COGWHEEL = model("large_cogwheel"),
            MODEL_SHAFT = model("shaft"),
            MODEL_CRUSHING_WHEEL = model("crushing_wheel"),
            MODEL_FLYWHEEL = model("flywheel"),
            MODEL_WATER_WHEEL = model("water_wheel"),
            MODEL_LARGE_WATER_WHEEL = model("large_water_wheel"),
            MODEL_LARGE_WATER_WHEEL_EXTENSION = model("large_water_wheel_extension");

    public static final java.util.Map<String, String> PART_ROTORS = java.util.Map.ofEntries(
            java.util.Map.entry("create:block/shaft", "shaft"),
            java.util.Map.entry("create:block/shaft_half", "part_shaft_half"),
            java.util.Map.entry("create:block/encased_shaft/shaft", "part_encased_shaft"),
            java.util.Map.entry("create:block/encased_fan/propeller", "part_fan_propeller"),
            java.util.Map.entry("create:block/millstone/inner", "part_millstone"),
            java.util.Map.entry("create:block/mechanical_drill/head", "part_drill_head"),
            java.util.Map.entry("create:block/hand_crank/block", "part_hand_crank"),
            java.util.Map.entry("create:block/turntable", "part_turntable"),
            java.util.Map.entry("create:block/mechanical_pump/cog", "part_pump_cog"),
            java.util.Map.entry("create:block/polymer/cogwheel_shaftless", "part_shaftless_cog"),
            java.util.Map.entry("create:block/mechanical_arm/cog", "part_arm_cog"));

    public static int partRotor(net.minecraft.resources.@org.jspecify.annotations.Nullable Identifier partModel) {
        String name = partModel == null ? null : PART_ROTORS.get(partModel.toString());
        return name == null ? -1 : model(name);
    }

    private static final int REFRESH_TICKS = 10;

    public static final class Rotor {
        private final Set<ServerPlayer> viewers = new HashSet<>();
        private final int refreshPhase;
        private int entityId = -1;
        private UUID uuid;
        private int model = -1;
        private Axis axis;
        private boolean flip;
        private float speed = Float.NaN;
        private BlockPos pos;

        public Rotor(BlockPos pos) {
            this.refreshPhase = Math.floorMod(pos.hashCode(), REFRESH_TICKS);
        }

        public void update(
                ServerLevel level, BlockPos pos, Axis axis, int model, float speed, float angle, long gameTime) {
            update(level, pos, axis, false, model, speed, angle, gameTime);
        }

        public void update(
                ServerLevel level,
                BlockPos pos,
                Axis axis,
                boolean flip,
                int model,
                float speed,
                float angle,
                long gameTime) {
            boolean changed = model != this.model
                    || axis != this.axis
                    || flip != this.flip
                    || speed != this.speed
                    || !pos.equals(this.pos);
            if (changed) {
                despawnAll();
                this.model = model;
                this.axis = axis;
                this.flip = flip;
                this.speed = speed;
                this.pos = pos.immutable();
                entityId = VirtualEntityUtils.requestEntityId();
                uuid = encode(model, axis, flip, angle, speed * 0.3f * 20);
            }
            if (!changed && Math.floorMod(gameTime + refreshPhase, REFRESH_TICKS) != 0) return;
            List<ServerPlayer> tracking = level.getChunkSource().chunkMap.getPlayers(ChunkPos.containing(pos), false);
            viewers.removeIf(player -> {
                if (tracking.contains(player) && !player.hasDisconnected()) return false;
                if (!player.hasDisconnected()) player.connection.send(new ClientboundRemoveEntitiesPacket(entityId));
                return true;
            });
            if (changed) {
                for (ServerPlayer player : viewers) spawn(player);
            }
            for (ServerPlayer player : tracking) {
                if (viewers.contains(player) || !PolymerBedrockCarriers.isBedrock(player.getUUID())) continue;
                viewers.add(player);
                spawn(player);
            }
        }

        private void spawn(ServerPlayer player) {
            player.connection.send(new ClientboundAddEntityPacket(
                    entityId,
                    uuid,
                    pos.getX() + 0.5,
                    pos.getY(),
                    pos.getZ() + 0.5,
                    0,
                    0,
                    EntityTypes.ARMOR_STAND,
                    0,
                    Vec3.ZERO,
                    0));
        }

        private void despawnAll() {
            if (entityId < 0) return;
            for (ServerPlayer player : viewers)
                if (!player.hasDisconnected()) player.connection.send(new ClientboundRemoveEntitiesPacket(entityId));
        }

        public void destroy() {
            despawnAll();
            viewers.clear();
        }
    }

    static UUID encode(int model, Axis axis, boolean flip, float angleDegrees, float degreesPerSecond) {
        int start = Math.floorMod(Math.round(angleDegrees), 360);
        long high = (model & 0x3FF) | ((long) axis.ordinal() << 10) | ((long) start << 12) | (flip ? 1L << 21 : 0);
        long low = Float.floatToIntBits(degreesPerSecond) & 0xFFFFFFFFL;
        return new UUID(MARKER, (high << 32) | low);
    }
}
