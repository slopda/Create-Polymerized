package com.zurrtum.create.infrastructure.polymer.contraption;

import com.zurrtum.create.infrastructure.packet.s2c.LimbSwingUpdatePacket;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import dev.veeda.createpoly.mixin.ObserverChunkMapAccessor;
import dev.veeda.createpoly.mixin.ObserverMovePacketAccessor;
import dev.veeda.createpoly.mixin.ObserverTrackedEntityAccessor;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.PositionPath;
import net.minecraft.world.phys.Vec3;

public final class PolymerObserverPlayers implements ModInitializer {
    private record History(PolymerShiftCarry.ObserverSupport support) {}

    private record Pair(ServerPlayer viewer, ServerPlayer subject) {}

    public static final int DEFAULT_LEAD = 3;

    private static final Map<UUID, History> HISTORY = new HashMap<>();
    private static final Map<UUID, Map<Integer, Pair>> PAIRS = new HashMap<>();
    private static volatile Map<UUID, Set<Integer>> ACTIVE = Map.of();

    public interface Trial {
        int lead(ServerPlayer viewer);

        void supported(
                ServerPlayer subject,
                PolymerShiftCarry.ObserverSupport support,
                PolymerShiftCarry.ObserverSupport prior);

        void lost(Set<UUID> subjects, long now);

        void snapBack(double distance);

        void aligned(int lead, double ahead);
    }

    private static Trial trial;

    @Override
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(PolymerObserverPlayers::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            clearViewer(handler.player);
            HISTORY.remove(handler.player.getUUID());
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (ServerPlayer viewer : NativePresentationPolicy.onlinePlayers(server.getPlayerList()))
                clearViewer(viewer);
            HISTORY.clear();
            ACTIVE = Map.of();
            PAIRS.clear();
        });
    }

    public static void install(Trial installed) {
        if (trial != null) throw new IllegalStateException("Observer trial already installed");
        trial = installed;
    }

    static int lead(ServerPlayer viewer) {
        return PolymerNativeClients.isNative(viewer) ? -1 : trial == null ? DEFAULT_LEAD : trial.lead(viewer);
    }

    public static boolean suppress(ServerPlayer viewer, Packet<?> packet) {
        Set<Integer> ids = ACTIVE.get(viewer.getUUID());
        if (ids == null || ids.isEmpty()) return false;
        int id;
        if (packet instanceof ClientboundMoveEntityPacket move)
            id = ((ObserverMovePacketAccessor) move).polymerpatch$observerEntityId();
        else if (packet instanceof ClientboundEntityPositionSyncPacket sync) id = sync.id();
        else if (packet instanceof ClientboundTeleportEntityPacket teleport) id = teleport.id();
        else return false;
        return id != viewer.getId() && ids.contains(id);
    }

    public static void acknowledgeFrame(ServerPlayer player, int teleportId) {
        PolymerObserverFrameHistory.acknowledge(
                player.getUUID(), teleportId, player.level().getGameTime());
    }

    public static void acceptFramePosition(ServerPlayer player, ServerboundMovePlayerPacket packet) {
        if (!packet.hasPosition()) return;
        if (PolymerNativeClients.isNative(player)
                || !player.isAlive()
                || player.isSpectator()
                || player.isPassenger()
                || player.getAbilities().flying) {
            PolymerObserverFrameHistory.clear(player.getUUID());
            return;
        }
        Vec3 reported = new Vec3(packet.getX(Double.NaN), packet.getY(Double.NaN), packet.getZ(Double.NaN));
        if (!Double.isFinite(reported.lengthSqr()) || reported.distanceToSqr(player.position()) > 1e-8) {
            PolymerObserverFrameHistory.clear(player.getUUID());
            return;
        }
        PolymerObserverFrameHistory.report(
                player.getUUID(),
                player.level().dimension(),
                player.position(),
                player.level().getGameTime());
    }

    static Vec3 forecast(PolymerShiftCarry.ObserverSupport support, int lead) {
        var previous = support.previous();
        var current = support.current();
        if (lead <= 0
                || previous.x().dot(current.x()) < .98
                || previous.z().dot(current.z()) < .98
                || previous.origin().distanceToSqr(current.origin()) > 4) return support.feet();
        Vec3 at = support.feet();
        for (int step = 0; step < lead; step++) at = current.toWorld(previous.toLocal(at));
        Vec3 motion = at.subtract(support.feet());
        if (!Double.isFinite(motion.lengthSqr())) return support.feet();
        if (motion.lengthSqr() > 64) motion = motion.normalize().scale(8);
        return support.feet().add(motion);
    }

    static float stride(ServerPlayer subject, PolymerShiftCarry.ObserverSupport support, History prior) {
        var input = subject.getLastClientInput();
        if (prior == null
                || prior.support.entity() != support.entity()
                || !(input.forward() || input.backward() || input.left() || input.right())) return 0;
        Vec3 delta = support.localFeet().subtract(prior.support.localFeet());
        return (float) Math.min(1, Math.sqrt(delta.x * delta.x + delta.z * delta.z) * 4);
    }

    static void tick(MinecraftServer server) {
        List<ServerPlayer> online = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) online.addAll(level.players());
        boolean nativeViewer = online.stream().anyMatch(PolymerNativeClients::isNative);
        boolean aligned = online.stream().anyMatch(viewer -> lead(viewer) >= 0);
        if (!nativeViewer && !aligned) {
            HISTORY.clear();
            clearAll();
            return;
        }
        Map<UUID, Map<Integer, Pair>> next = new HashMap<>();
        Set<UUID> supported = new HashSet<>();
        for (ServerPlayer subject : online) {
            var support = PolymerShiftCarry.observerSupport(subject);
            if (support == null) continue;
            supported.add(subject.getUUID());
            History prior = HISTORY.put(subject.getUUID(), new History(support));
            if (trial != null) trial.supported(subject, support, prior == null ? null : prior.support);
            float stride = stride(subject, support, prior);
            for (ServerPlayer viewer : PlayerLookup.tracking(subject)) {
                if (viewer == subject || viewer.level() != subject.level()) continue;
                if (PolymerNativeClients.isNative(viewer)) {
                    if (!PolymerNativeClients.isNative(subject)) nativeUpdate(viewer, subject, stride);
                } else if (lead(viewer) >= 0)
                    next.computeIfAbsent(viewer.getUUID(), id -> new HashMap<>())
                            .put(subject.getId(), new Pair(viewer, subject));
            }
        }
        if (trial != null) {
            Set<UUID> lost = new HashSet<>(HISTORY.keySet());
            lost.removeAll(supported);
            trial.lost(lost, server.overworld().getGameTime());
        }
        HISTORY.keySet().retainAll(supported);
        for (var entry : PAIRS.entrySet())
            for (var old : entry.getValue().entrySet())
                if (!next.getOrDefault(entry.getKey(), Map.of()).containsKey(old.getKey())) {
                    double snap = restore(old.getValue());
                    if (snap >= 0 && trial != null) trial.snapBack(snap);
                }
        Map<UUID, Set<Integer>> active = new HashMap<>();
        next.forEach((id, pairs) -> active.put(id, Set.copyOf(pairs.keySet())));
        ACTIVE = Map.copyOf(active);
        PAIRS.clear();
        PAIRS.putAll(next);
        for (var pairs : next.values())
            for (Pair pair : pairs.values()) {
                History history = HISTORY.get(pair.subject.getUUID());
                if (history == null) continue;
                int lead = lead(pair.viewer);
                Vec3 target = forecast(history.support, lead);
                if (trial != null) trial.aligned(lead, target.distanceTo(history.support.feet()));
                var values = new PositionMoveRotation(
                        target, pair.subject.getDeltaMovement(), pair.subject.getYRot(), pair.subject.getXRot());
                pair.viewer.connection.connection.send(new ClientboundEntityPositionSyncPacket(
                        pair.subject.getId(),
                        PositionPath.of(values.position()),
                        values.yRot(),
                        values.xRot(),
                        pair.subject.onGround()));
            }
    }

    private static void nativeUpdate(ServerPlayer viewer, ServerPlayer subject, float stride) {
        viewer.connection.send(new LimbSwingUpdatePacket(subject.getId(), subject.position(), stride));
        NativePresentationPolicy.sendObserverRotation(
                viewer,
                new ClientboundMoveEntityPacket.Rot(
                        subject.getId(),
                        (byte) Mth.floor(subject.getYRot() * 256f / 360f),
                        (byte) Mth.floor(subject.getXRot() * 256f / 360f),
                        subject.onGround()));
    }

    static ServerEntity tracker(ServerPlayer subject) {
        Object tracked = ((ObserverChunkMapAccessor) subject.level().getChunkSource().chunkMap)
                .polymerpatch$observerTrackedEntities()
                .get(subject.getId());
        return tracked instanceof ObserverTrackedEntityAccessor accessor
                ? accessor.polymerpatch$observerTracker()
                : null;
    }

    private static double restore(Pair pair) {
        if (pair.viewer.isRemoved() || pair.subject.isRemoved() || pair.viewer.level() != pair.subject.level())
            return -1;
        ServerEntity tracker = tracker(pair.subject);
        if (tracker == null) return -1;
        var values = new PositionMoveRotation(
                tracker.getPositionBase(),
                pair.subject.getDeltaMovement(),
                tracker.getLastSentYRot(),
                tracker.getLastSentXRot());
        pair.viewer.connection.connection.send(new ClientboundEntityPositionSyncPacket(
                pair.subject.getId(),
                PositionPath.of(values.position()),
                values.yRot(),
                values.xRot(),
                pair.subject.onGround()));
        return tracker.getPositionBase().distanceTo(pair.subject.position());
    }

    private static void clearAll() {
        if (PAIRS.isEmpty()) return;
        for (var pairs : PAIRS.values()) pairs.values().forEach(PolymerObserverPlayers::restore);
        PAIRS.clear();
        ACTIVE = Map.of();
    }

    public static void clearViewer(ServerPlayer viewer) {
        Map<Integer, Pair> pairs = PAIRS.remove(viewer.getUUID());
        if (pairs != null) pairs.values().forEach(PolymerObserverPlayers::restore);
        Map<UUID, Set<Integer>> active = new HashMap<>(ACTIVE);
        active.remove(viewer.getUUID());
        ACTIVE = Map.copyOf(active);
    }
}
