package com.zurrtum.create.infrastructure.polymer.collision;

import com.zurrtum.create.infrastructure.polymer.PolymerReach;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class PolymerStaticProxyRange {
    private static final Map<UUID, State> STATES = new HashMap<>();
    private static Predicate<ServerPlayer> legacy;

    private static final class State {
        final ServerPlayer owner;
        ServerLevel level;
        int tick = Integer.MIN_VALUE;
        AABB enter, retain;
        final Map<BlockPos, Integer> targets = new HashMap<>();

        State(ServerPlayer owner) {
            this.owner = owner;
        }
    }

    private PolymerStaticProxyRange() {}

    static void register() {
        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> STATES.remove(handler.getPlayer().getUUID()));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> STATES.clear());
    }

    public static void legacyRange(Predicate<ServerPlayer> override) {
        if (legacy != null) throw new IllegalStateException("Static proxy range override already installed");
        legacy = override;
    }

    public static void forget(ServerPlayer player) {
        STATES.remove(player.getUUID());
    }

    static boolean near(ServerPlayer player, ServerLevel level, BlockPos pos, AABB worldBox, boolean watching) {
        if (legacy != null && legacy.test(player) || !level.getServer().isSameThread())
            return player.position().distanceToSqr(Vec3.atCenterOf(pos)) < 144;
        State state = STATES.get(player.getUUID());
        if (state == null || state.owner != player) STATES.put(player.getUUID(), state = new State(player));
        int tick = level.getServer().getTickCount();
        if (state.level != level || state.tick != tick) {
            if (state.level != level) state.targets.clear();
            state.level = level;
            state.tick = tick;
            double speed = player.getKnownMovement().length();
            double padding = Double.isFinite(speed) ? Math.min(8, speed * 10) : 8;
            state.enter = player.getBoundingBox().inflate(4 + padding);
            state.retain = player.getBoundingBox().inflate(6 + padding);
            state.targets.entrySet().removeIf(entry -> tick - entry.getValue() > 10);
            if (rayLoaded(player, level)) {
                BlockHitResult block = PolymerReach.targetBlock(player);
                if (block != null) state.targets.put(block.getBlockPos().immutable(), tick);
            }
        }
        return (watching ? state.retain : state.enter).intersects(worldBox) || state.targets.containsKey(pos);
    }

    private static boolean rayLoaded(ServerPlayer player, ServerLevel level) {
        double reach = player.blockInteractionRange();
        if (!Double.isFinite(reach) || reach < 0 || reach > 128) return false;
        Vec3 start = player.getEyePosition(),
                end = start.add(player.getLookAngle().scale(reach));
        BlockPos a = BlockPos.containing(start), b = BlockPos.containing(end);
        for (int x = Math.min(a.getX(), b.getX()) >> 4; x <= (Math.max(a.getX(), b.getX()) >> 4); x++)
            for (int z = Math.min(a.getZ(), b.getZ()) >> 4; z <= (Math.max(a.getZ(), b.getZ()) >> 4); z++)
                if (level.getChunkSource().getChunkNow(x, z) == null) return false;
        return true;
    }
}
