package com.zurrtum.create.infrastructure.polymer.contraption;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class PolymerBeltPlayers {
    private PolymerBeltPlayers() {}

    private static final Map<UUID, Vec3> PREVIOUS = new HashMap<>();
    private static final Map<UUID, Long> LAST = new HashMap<>();

    private static final ScheduledExecutorService STEPS = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "polymerpatch-belt-steps");
        thread.setDaemon(true);
        return thread;
    });

    public static void push(ServerPlayer player, Vec3 moved) {
        var connection = player.connection.connection;
        Vec3 step = moved.scale(1.0 / PolymerShiftCarry.SUBSTEPS);
        for (int k = 0; k < PolymerShiftCarry.SUBSTEPS; k++) {
            var packet = PolymerShiftCarry.shiftPacket(step, 0);
            if (k == 0) NativePresentationPolicy.sendPresentation(connection, packet);
            else
                STEPS.schedule(
                        () -> {
                            if (connection.isConnected()) NativePresentationPolicy.sendPresentation(connection, packet);
                        },
                        k * PolymerShiftCarry.STEP_MS,
                        TimeUnit.MILLISECONDS);
        }
        player.connection.resetFlyingTicks();
        player.resetFallDistance();
    }

    public static void register() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            PREVIOUS.remove(handler.player.getUUID());
            LAST.remove(handler.player.getUUID());
        });
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            for (ServerPlayer player : NativePresentationPolicy.presentationPlayers(server.getPlayerList()))
                tick(player);
        });
    }

    private static void tick(ServerPlayer player) {
        if (player.isSpectator()
                || player.isPassenger()
                || player.getAbilities().flying
                || !(player.level() instanceof ServerLevel level)) return;
        BlockPos feet = player.blockPosition();
        BlockPos belt = null;
        if (level.getBlockState(feet).is(AllBlocks.BELT)) belt = feet;
        else if (level.getBlockState(feet.below()).is(AllBlocks.BELT)) belt = feet.below();
        if (belt == null) return;
        double height = player.getY() - belt.getY();
        if (height < 0.5 || height > 1.25) return;
        BlockState state = level.getBlockState(belt);
        AllBlocks.BELT.entityInside(state, level, belt, player, InsideBlockEffectApplier.NOOP, false);
    }
}
