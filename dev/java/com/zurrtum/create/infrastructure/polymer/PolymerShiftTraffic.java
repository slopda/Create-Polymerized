package com.zurrtum.create.infrastructure.polymer;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;

final class PolymerShiftTraffic {
    static final int TELEPORTS = 0,
            SPAWNS = 1,
            REMOVED_IDS = 2,
            ENTITY_DATA = 3,
            PLAYER_SHIFTS = 4,
            BUNDLES = 5,
            OTHER = 6,
            KINDS = 7;
    static final String[] NAMES = {"teleports", "spawns", "removedIds", "entityData", "playerShifts", "bundles", "other"
    };

    private static final Map<UUID, Viewer> VIEWERS = new HashMap<>();

    static final class Viewer {
        final long[] current = new long[KINDS], last = new long[KINDS];
        long windowStart = -1, lastWindowTicks;
        final Map<Object, int[]> gauges = new HashMap<>();
    }

    private PolymerShiftTraffic() {}

    static void record(UUID viewer, long gameTime, Collection<? extends Packet<?>> bundle) {
        if (bundle.isEmpty()) return;
        Viewer state = viewer(viewer, gameTime);
        state.current[BUNDLES]++;
        for (Packet<?> packet : bundle) count(state, packet);
    }

    static void gauge(UUID viewer, long gameTime, Object carrier, int cells, int copies, boolean seated) {
        viewer(viewer, gameTime).gauges.put(carrier, new int[] {cells, copies, seated ? 1 : 0});
    }

    static void forgetCarrier(UUID viewer, Object carrier) {
        Viewer state = VIEWERS.get(viewer);
        if (state != null) state.gauges.remove(carrier);
    }

    static void forget(UUID viewer) {
        VIEWERS.remove(viewer);
    }

    static void clear() {
        VIEWERS.clear();
    }

    private static void count(Viewer state, Packet<?> packet) {
        if (packet instanceof ClientboundBundlePacket bundle) {
            for (Packet<?> inner : bundle.subPackets()) count(state, inner);
        } else if (packet instanceof ClientboundTeleportEntityPacket) state.current[TELEPORTS]++;
        else if (packet instanceof ClientboundAddEntityPacket) state.current[SPAWNS]++;
        else if (packet instanceof ClientboundRemoveEntitiesPacket remove)
            state.current[REMOVED_IDS] += remove.getEntityIds().size();
        else if (packet instanceof ClientboundSetEntityDataPacket) state.current[ENTITY_DATA]++;
        else if (packet instanceof ClientboundPlayerPositionPacket) state.current[PLAYER_SHIFTS]++;
        else state.current[OTHER]++;
    }

    private static Viewer viewer(UUID id, long gameTime) {
        Viewer state = VIEWERS.computeIfAbsent(id, ignored -> new Viewer());
        if (state.windowStart < 0) state.windowStart = gameTime;
        long elapsed = gameTime - state.windowStart;
        if (elapsed >= 20 || elapsed < 0) {
            System.arraycopy(state.current, 0, state.last, 0, KINDS);
            Arrays.fill(state.current, 0);
            state.lastWindowTicks = elapsed < 0 ? 0 : elapsed;
            state.windowStart = gameTime;
        }
        return state;
    }

    static Snapshot snapshot(UUID id) {
        Viewer state = VIEWERS.get(id);
        if (state == null) return null;
        int cells = 0, copies = 0, seated = 0;
        for (int[] gauge : state.gauges.values()) {
            cells += gauge[0];
            copies += gauge[1];
            seated += gauge[2];
        }
        return new Snapshot(state.last.clone(), state.lastWindowTicks, cells, copies, seated, state.gauges.size());
    }

    record Snapshot(long[] counts, long ticks, int cells, int copies, int seatedCarriers, int carriers) {
        String describe() {
            StringBuilder text =
                    new StringBuilder("Shift traffic, last ").append(ticks).append(" ticks: ");
            for (int i = 0; i < KINDS; i++)
                text.append(NAMES[i]).append('=').append(counts[i]).append(i + 1 < KINDS ? ", " : "");
            return text.append(". Now: ")
                    .append(cells)
                    .append(" cells, ")
                    .append(copies)
                    .append(" display copies on ")
                    .append(carriers)
                    .append(" carriers (")
                    .append(seatedCarriers)
                    .append(" seated).")
                    .toString();
        }
    }
}
