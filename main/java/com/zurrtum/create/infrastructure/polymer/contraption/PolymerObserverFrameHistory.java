package com.zurrtum.create.infrastructure.polymer.contraption;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

final class PolymerObserverFrameHistory {
    private static final int MAX_PENDING_PER_PLAYER = 128;
    private static final int MAX_RETIRED_PER_PLAYER = 256;
    private static final int MAX_PLAYERS = 256;
    private static final long FRAME_TTL_TICKS = 100;
    private static final long REPORT_TTL_TICKS = 20;

    private static final LinkedHashMap<UUID, PlayerHistory> PLAYERS = new LinkedHashMap<>(16, .75f, true);

    private PolymerObserverFrameHistory() {}

    record Frame(
            UUID owner,
            ResourceKey<Level> dimension,
            PolymerContraptionPhysics.Transform pose,
            long sentTick,
            long sequence) {}

    record Report(Frame frame, Vec3 feet, Vec3 localFeet, long acceptedTick) {}

    private record Retired(long sequence, long retiredTick) {}

    private static final class PlayerHistory {
        final LinkedHashMap<Integer, Frame> pending = new LinkedHashMap<>();
        final LinkedHashMap<Integer, Retired> retired = new LinkedHashMap<>();
        long nextSequence;
        long highestAcknowledgedSequence;
        Frame acknowledged;
        int acknowledgedId;
        long acknowledgedTick = Long.MIN_VALUE;
        Report report;
    }

    static synchronized void publish(
            UUID player,
            int negativeTeleportId,
            UUID contraption,
            ResourceKey<Level> dimension,
            PolymerContraptionPhysics.Transform pose,
            long tick) {
        if (player == null) return;
        PlayerHistory history = PLAYERS.get(player);
        if (negativeTeleportId >= 0 || contraption == null || dimension == null || !usable(pose) || tick < 0) {
            if (history != null) invalidate(history, tick);
            return;
        }
        if (history == null) history = create(player);
        expire(history, tick);

        if (history.nextSequence == Long.MAX_VALUE) {
            invalidate(history, tick);
            history.nextSequence = 0;
            history.highestAcknowledgedSequence = 0;
        }
        Frame frame = new Frame(contraption, dimension, pose, tick, ++history.nextSequence);
        Frame replaced = history.pending.remove(negativeTeleportId);
        if (replaced != null) rememberRetired(history, negativeTeleportId, replaced.sequence(), tick);
        history.retired.remove(negativeTeleportId);

        while (history.pending.size() >= MAX_PENDING_PER_PLAYER) {
            Map.Entry<Integer, Frame> oldest =
                    history.pending.entrySet().iterator().next();
            history.pending.remove(oldest.getKey());
            rememberRetired(history, oldest.getKey(), oldest.getValue().sequence(), tick);
        }
        history.pending.put(negativeTeleportId, frame);
    }

    static synchronized void acknowledge(UUID player, int id, long tick) {
        if (player == null) return;
        PlayerHistory history = PLAYERS.get(player);
        if (history == null) return;
        expire(history, tick);

        if (id >= 0) {
            invalidate(history, tick);
            return;
        }

        Frame frame = history.pending.remove(id);
        if (frame == null) {
            if (!history.retired.containsKey(id)) invalidate(history, tick);
            return;
        }
        rememberRetired(history, id, frame.sequence(), tick);
        if (!fresh(tick, frame.sentTick(), FRAME_TTL_TICKS)) return;
        if (frame.sequence() <= history.highestAcknowledgedSequence) {
            retireOlder(history, frame.sequence(), tick);
            return;
        }

        history.highestAcknowledgedSequence = frame.sequence();
        retireOlder(history, frame.sequence(), tick);
        if (history.report != null
                && (!history.report.frame().owner().equals(frame.owner())
                        || !history.report.frame().dimension().equals(frame.dimension()))) history.report = null;
        history.acknowledged = frame;
        history.acknowledgedId = id;
        history.acknowledgedTick = tick;
    }

    static synchronized void report(UUID player, ResourceKey<Level> dimension, Vec3 acceptedFeet, long tick) {
        if (player == null) return;
        PlayerHistory history = PLAYERS.get(player);
        if (history == null) return;
        expire(history, tick);
        Frame frame = history.acknowledged;
        if (frame == null || tick < history.acknowledgedTick || !fresh(tick, frame.sentTick(), FRAME_TTL_TICKS)) return;
        if (dimension == null || !frame.dimension().equals(dimension) || !finite(acceptedFeet)) {
            invalidate(history, tick);
            return;
        }
        Vec3 localFeet = frame.pose().toLocal(acceptedFeet);
        if (!finite(localFeet)) {
            invalidate(history, tick);
            return;
        }
        history.report = new Report(frame, acceptedFeet, localFeet, tick);
    }

    static synchronized Report latest(UUID player, UUID contraption, ResourceKey<Level> dimension, long tick) {
        if (player == null || contraption == null || dimension == null) return null;
        PlayerHistory history = PLAYERS.get(player);
        if (history == null) return null;
        expire(history, tick);
        Report report = history.report;
        if (report == null) return null;
        Frame frame = report.frame();
        if (!frame.owner().equals(contraption)
                || !frame.dimension().equals(dimension)
                || !fresh(tick, frame.sentTick(), FRAME_TTL_TICKS)
                || !fresh(tick, report.acceptedTick(), REPORT_TTL_TICKS)
                || !finite(report.feet())
                || !finite(report.localFeet())
                || !usable(frame.pose())) return null;
        return report;
    }

    static synchronized void clear(UUID player) {
        if (player != null) PLAYERS.remove(player);
    }

    static synchronized void clearAll() {
        PLAYERS.clear();
    }

    private static PlayerHistory create(UUID player) {
        if (PLAYERS.size() >= MAX_PLAYERS) {
            Iterator<UUID> oldest = PLAYERS.keySet().iterator();
            if (oldest.hasNext()) {
                oldest.next();
                oldest.remove();
            }
        }
        PlayerHistory history = new PlayerHistory();
        PLAYERS.put(player, history);
        return history;
    }

    private static void expire(PlayerHistory history, long tick) {
        Iterator<Map.Entry<Integer, Frame>> pending = history.pending.entrySet().iterator();
        while (pending.hasNext()) {
            Map.Entry<Integer, Frame> entry = pending.next();
            if (!fresh(tick, entry.getValue().sentTick(), FRAME_TTL_TICKS)) {
                rememberRetired(history, entry.getKey(), entry.getValue().sequence(), tick);
                pending.remove();
            }
        }
        Iterator<Map.Entry<Integer, Retired>> retired =
                history.retired.entrySet().iterator();
        while (retired.hasNext()) {
            if (!fresh(tick, retired.next().getValue().retiredTick(), FRAME_TTL_TICKS)) retired.remove();
        }
        if (history.acknowledged != null && !fresh(tick, history.acknowledged.sentTick(), FRAME_TTL_TICKS)) {
            rememberRetired(history, history.acknowledgedId, history.acknowledged.sequence(), tick);
            history.acknowledged = null;
            history.acknowledgedId = 0;
            history.acknowledgedTick = Long.MIN_VALUE;
            history.report = null;
        }
        if (history.report != null && !fresh(tick, history.report.acceptedTick(), REPORT_TTL_TICKS))
            history.report = null;
    }

    private static void retireOlder(PlayerHistory history, long sequence, long tick) {
        Iterator<Map.Entry<Integer, Frame>> pending = history.pending.entrySet().iterator();
        while (pending.hasNext()) {
            Map.Entry<Integer, Frame> entry = pending.next();
            if (entry.getValue().sequence() <= sequence) {
                rememberRetired(history, entry.getKey(), entry.getValue().sequence(), tick);
                pending.remove();
            }
        }
    }

    private static void invalidate(PlayerHistory history, long tick) {
        for (Map.Entry<Integer, Frame> entry : history.pending.entrySet())
            rememberRetired(history, entry.getKey(), entry.getValue().sequence(), tick);
        history.pending.clear();
        if (history.acknowledged != null)
            rememberRetired(history, history.acknowledgedId, history.acknowledged.sequence(), tick);
        history.acknowledged = null;
        history.acknowledgedId = 0;
        history.acknowledgedTick = Long.MIN_VALUE;
        history.report = null;
    }

    private static void rememberRetired(PlayerHistory history, int id, long sequence, long tick) {
        if (id >= 0) return;
        history.retired.remove(id);
        history.retired.put(id, new Retired(sequence, tick));
        while (history.retired.size() > MAX_RETIRED_PER_PLAYER)
            history.retired.remove(history.retired.keySet().iterator().next());
    }

    private static boolean fresh(long now, long then, long ttl) {
        return now >= then && now - then <= ttl;
    }

    private static boolean usable(PolymerContraptionPhysics.Transform pose) {
        if (pose == null || !finite(pose.origin()) || !finite(pose.x()) || !finite(pose.y()) || !finite(pose.z()))
            return false;
        double determinant = pose.x().dot(pose.y().cross(pose.z()));
        return Double.isFinite(determinant) && Math.abs(determinant) > 1e-9;
    }

    private static boolean finite(Vec3 point) {
        return point != null && Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }
}
