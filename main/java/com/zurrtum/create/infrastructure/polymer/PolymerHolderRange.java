package com.zurrtum.create.infrastructure.polymer;

import eu.pb4.polymer.virtualentity.api.VirtualEntityUtils;
import eu.pb4.polymer.virtualentity.api.attachment.ChunkAttachment;
import eu.pb4.polymer.virtualentity.api.attachment.HolderAttachment;
import eu.pb4.polymer.virtualentity.impl.HolderAttachmentHolder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

public final class PolymerHolderRange {
    public static boolean ENABLED = true;

    public static double START, STOP;
    private static double START_SQR, STOP_SQR;
    private static int RING;

    static {
        range(96);
    }

    private static final Map<ServerPlayer, Scan> SCANS = new WeakHashMap<>();
    private static boolean registered;

    private static final class Scan {
        ServerLevel level;
        Vec3 at;
        int tick;
        boolean enabled;
    }

    private PolymerHolderRange() {}

    public static void register() {
        if (registered) return;
        registered = true;
        PolymerAdapterConfig.load();
        ENABLED = PolymerAdapterConfig.blockHolderGate;
        range(Math.max(96, Math.ceil(1.25 * PolymerAdapterConfig.blockDisplayDistance) + 16));
        ServerTickEvents.END_SERVER_TICK.register(PolymerHolderRange::tick);
    }

    private static void range(double start) {
        START = start;
        STOP = start + 16;
        START_SQR = START * START;
        STOP_SQR = STOP * STOP;
        RING = (int) Math.ceil(STOP / 16) + 1;
    }

    static boolean allows(AdapterHolder holder, ServerPlayer player) {
        HolderAttachment attachment = holder.getAttachment();
        if (!ENABLED || !(attachment instanceof ChunkAttachment) || attachment.getWorld() != player.level())
            return true;
        return attachment.getPos().distanceToSqr(player.position()) <= START_SQR;
    }

    private static void tick(MinecraftServer server) {
        int now = server.getTickCount();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.hasDisconnected()) continue;
            ServerLevel level = player.level();
            Vec3 at = player.position();
            Scan scan = SCANS.computeIfAbsent(player, ignored -> new Scan());
            boolean jumped = scan.level != level || scan.at == null || scan.at.distanceToSqr(at) > 32 * 32;
            boolean toggled = scan.enabled != ENABLED;
            boolean moved = !jumped && scan.at.distanceToSqr(at) > 4;
            int age = now - scan.tick;
            if (!jumped && !toggled && !(moved && age >= 5) && age < 40) continue;
            int radius = jumped || toggled ? server.getPlayerList().getViewDistance() + 1 : RING;
            scan.level = level;
            scan.at = at;
            scan.tick = now;
            scan.enabled = ENABLED;
            rescan(player, level, radius);
        }
    }

    private static void rescan(ServerPlayer player, ServerLevel level, int radius) {
        var connection = player.connection;
        int cx = player.chunkPosition().x(), cz = player.chunkPosition().z();
        List<HolderAttachment> attachments = new ArrayList<>();
        for (int x = cx - radius; x <= cx + radius; x++)
            for (int z = cz - radius; z <= cz + radius; z++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
                if (!(chunk instanceof HolderAttachmentHolder holders)) continue;
                if (!VirtualEntityUtils.isPlayerTracking(player, chunk)) continue;
                attachments.clear();
                attachments.addAll(holders.polymerVE$getHolders());
                for (HolderAttachment attachment : attachments) {
                    if (!(attachment instanceof ChunkAttachment)
                            || attachment.isRemoved()
                            || !(attachment.holder() instanceof AdapterHolder holder)) continue;
                    boolean watching = holder.getWatchingPlayers().contains(connection);
                    double distance = attachment.getPos().distanceToSqr(player.position());
                    if (watching && ENABLED && distance > STOP_SQR) attachment.stopWatching(connection);
                    else if (!watching && (!ENABLED || distance <= START_SQR)) attachment.startWatching(connection);
                }
            }
    }
}
