package com.zurrtum.create.infrastructure.polymer;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.zurrtum.create.infrastructure.polymer.contraption.PolymerShiftCarry;
import com.zurrtum.create.infrastructure.polymer.contraption.PolymerShiftCellLimit;
import com.zurrtum.create.infrastructure.polymer.contraption.PolymerShiftDiagnostics;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;

public final class PolymerShiftCellTrial implements ModInitializer {
    private static final Map<UUID, Integer> LIMITS = new ConcurrentHashMap<>();

    @Override
    public void onInitialize() {
        PolymerShiftCellLimit.override(player -> LIMITS.getOrDefault(player.getUUID(), 0));
        PolymerShiftDiagnostics.install(new PolymerShiftDiagnostics.Recorder() {
            @Override
            public void record(UUID viewer, long gameTime, Collection<? extends Packet<?>> bundle) {
                PolymerShiftTraffic.record(viewer, gameTime, bundle);
            }

            @Override
            public void gauge(UUID viewer, long gameTime, Object carrier, int cells, int copies, boolean seated) {
                PolymerShiftTraffic.gauge(viewer, gameTime, carrier, cells, copies, seated);
            }

            @Override
            public void forgetCarrier(UUID viewer, Object carrier) {
                PolymerShiftTraffic.forgetCarrier(viewer, carrier);
            }

            @Override
            public void forget(UUID viewer) {
                PolymerShiftTraffic.forget(viewer);
            }

            @Override
            public void clear() {
                PolymerShiftTraffic.clear();
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> LIMITS.clear());
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
                dispatcher.register(Commands.literal("createfly")
                        .then(Commands.literal("cells")
                                .then(Commands.literal("off").executes(context -> {
                                    setLimit(context.getSource().getPlayerOrException(), -1);
                                    return 1;
                                }))
                                .then(Commands.literal("stats").executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    var snapshot = PolymerShiftTraffic.snapshot(player.getUUID());
                                    player.sendSystemMessage(Component.literal(
                                            snapshot == null
                                                    ? "No moving-contraption collision traffic has been built for you yet."
                                                    : snapshot.describe() + " Cell limit "
                                                            + PolymerShiftCellLimit.limit(player) + "."));
                                    return 1;
                                }))
                                .then(Commands.literal("limit")
                                        .then(Commands.argument(
                                                        "count",
                                                        IntegerArgumentType.integer(32, PolymerShiftCarry.MAX_CELLS))
                                                .executes(context -> {
                                                    setLimit(
                                                            context.getSource().getPlayerOrException(),
                                                            IntegerArgumentType.getInteger(context, "count"));
                                                    return 1;
                                                }))))));
    }

    static void forget(ServerPlayer player) {
        LIMITS.remove(player.getUUID());
    }

    static void setLimit(ServerPlayer player, int count) {
        int standard = PolymerShiftCellLimit.DEFAULT;
        if (count != -1 && (count < 32 || count > PolymerShiftCarry.MAX_CELLS))
            throw new IllegalArgumentException("Cell limit must be32..384 or-1");
        if (count == -1 || count == standard || PolymerNativeClients.isNative(player)) {
            forget(player);
            player.sendSystemMessage(Component.literal(
                    PolymerNativeClients.isNative(player)
                            ? "Native Create uses its own collision; the cell trial is disabled."
                            : "Collision detail restored to the default " + standard + " cells per contraption."));
        } else {
            LIMITS.put(player.getUUID(), count);
            player.sendSystemMessage(Component.literal("Collision detail: " + count + " cells per contraption (default "
                    + standard + "). /createfly cells off restores the default."));
        }
    }
}
