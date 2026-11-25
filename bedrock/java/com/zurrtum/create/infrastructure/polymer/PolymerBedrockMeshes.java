package com.zurrtum.create.infrastructure.polymer;

import eu.pb4.polymer.virtualentity.api.VirtualEntityUtils;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

public final class PolymerBedrockMeshes {
    private static final java.util.concurrent.ConcurrentMap<Integer, int[]> FRAMES =
            new java.util.concurrent.ConcurrentHashMap<>();

    static {
        System.getProperties()
                .put(PolymerBedrockMotion.SNAPSHOT_KEY, (java.util.function.IntFunction<int[]>) FRAMES::get);
    }

    private PolymerBedrockMeshes() {}

    public record Model(int geometry, int material) {
        public Model {
            if (geometry < 0 || geometry > 65535 || material < 0 || material > 65535)
                throw new IllegalArgumentException("Invalid Bedrock mesh index");
        }
    }

    public static final class Holder {
        private final long marker;
        private final String hookKey;
        private final double rangeSquared;
        private final int refreshPhase;
        private final Set<ServerPlayer> viewers = new HashSet<>();
        private int entityId = -1;
        private long pose;
        private Vec3 position = Vec3.ZERO;
        private int lastSequence = -1;

        public Holder(BlockPos identity, long marker, String hookKey, double range) {
            this.marker = marker;
            this.hookKey = hookKey;
            this.rangeSquared = range * range;
            this.refreshPhase = Math.floorMod(identity.hashCode(), 10);
        }

        public boolean uses(long meshMarker) {
            return marker == meshMarker;
        }

        public int entityId() {
            return entityId;
        }

        @SuppressWarnings("unchecked")
        private BiConsumer<UUID, int[]> poseHook() {
            return System.getProperties().get(hookKey) instanceof BiConsumer<?, ?> hook
                    ? (BiConsumer<UUID, int[]>) hook
                    : null;
        }

        public void update(
                ServerLevel level, BlockPos trackingAnchor, Vec3 centre, int[] angles, Model model, long gameTime) {
            updateState(level, trackingAnchor, centre, angles, model, gameTime, null, BlockPos.ZERO);
        }

        public void update(
                ServerLevel level,
                BlockPos trackingAnchor,
                Vec3 centre,
                int[] angles,
                Model model,
                long gameTime,
                PolymerBedrockMotion.Frame frame,
                BlockPos local) {
            updateState(level, trackingAnchor, centre, angles, model, gameTime, frame, local);
        }

        @SuppressWarnings("unchecked")
        private void updateState(
                ServerLevel level,
                BlockPos trackingAnchor,
                Vec3 centre,
                int[] angles,
                Model model,
                long gameTime,
                PolymerBedrockMotion.Frame frame,
                BlockPos local) {
            long next = PolymerBedrockMeshPose.encode(model.geometry(), model.material(), angles);
            Vec3 at = centre.add(0, -0.5, 0);
            boolean seedChanged = entityId < 0 || next != pose;
            boolean preciseChanged = frame != null && frame.sequence() != lastSequence;
            boolean moved = entityId >= 0 && position.distanceToSqr(at) > 1e-6;
            BiConsumer<UUID, int[]> hook = poseHook();
            Object frameService = System.getProperties().get(hookKey + PolymerBedrockMotion.HOOK_SUFFIX);
            BiConsumer<UUID, int[]> frameHook =
                    frameService instanceof BiConsumer<?, ?> service ? (BiConsumer<UUID, int[]>) service : null;
            boolean framed = frame != null && frameHook != null;
            boolean rebase =
                    framed && moved && position.distanceToSqr(at) > PolymerBedrockMotion.HOST_REBASE_DISTANCE_SQUARED;
            boolean changed = seedChanged || preciseChanged || rebase;
            boolean respawn = entityId < 0 || seedChanged && hook == null && !framed;
            if (respawn) {
                destroy();
                entityId = VirtualEntityUtils.requestEntityId();
            }
            pose = next;
            if (respawn || moved && (!framed || rebase)) position = at;
            int[] frameData =
                    framed ? (changed ? frame.data(entityId, model, local, position) : FRAMES.get(entityId)) : null;
            if (framed) {
                if (frameData == null) frameData = frame.data(entityId, model, local, position);
                if (rebase) frameData[4] = 1;
                if (changed) FRAMES.put(entityId, frameData);
                lastSequence = frame.sequence();
            } else {
                FRAMES.remove(entityId);
            }
            if (!respawn && changed) {
                if (framed) for (ServerPlayer viewer : viewers) frameHook.accept(viewer.getUUID(), frameData);
                else if (seedChanged && hook != null) {
                    int[] data = {entityId, model.geometry(), model.material(), angles[0], angles[1], angles[2]};
                    for (ServerPlayer viewer : viewers) hook.accept(viewer.getUUID(), data);
                }
            }
            boolean refresh = Math.floorMod(gameTime + refreshPhase, 10) == 0;
            if (!respawn && !changed && !moved && !refresh) return;
            var tracking = level.getChunkSource().chunkMap.getPlayers(ChunkPos.containing(trackingAnchor), false);
            viewers.removeIf(viewer -> {
                boolean keep = !viewer.hasDisconnected()
                        && tracking.contains(viewer)
                        && viewer.position().distanceToSqr(centre) < rangeSquared;
                if (!keep && !viewer.hasDisconnected())
                    viewer.connection.send(new ClientboundRemoveEntitiesPacket(entityId));
                return !keep;
            });
            if (moved && !respawn && !framed)
                for (ServerPlayer viewer : viewers)
                    viewer.connection.send(new ClientboundTeleportEntityPacket(
                            entityId, new PositionMoveRotation(position, Vec3.ZERO, 0, 0), Set.of(), false));
            for (ServerPlayer viewer : tracking) {
                if (viewers.contains(viewer)
                        || viewer.hasDisconnected()
                        || !PolymerBedrockCarriers.isBedrock(viewer.getUUID())
                        || viewer.position().distanceToSqr(centre) >= rangeSquared) continue;
                viewers.add(viewer);
                viewer.connection.send(new ClientboundAddEntityPacket(
                        entityId,
                        new UUID(marker, pose),
                        position.x,
                        position.y,
                        position.z,
                        0,
                        0,
                        EntityTypes.ARMOR_STAND,
                        0,
                        Vec3.ZERO,
                        0));
            }
        }

        public void destroy() {
            FRAMES.remove(entityId);
            if (entityId >= 0)
                for (ServerPlayer viewer : viewers)
                    if (!viewer.hasDisconnected())
                        viewer.connection.send(new ClientboundRemoveEntitiesPacket(entityId));
            viewers.clear();
            entityId = -1;
            lastSequence = -1;
        }
    }
}
