package com.zurrtum.create.infrastructure.polymer;

import eu.pb4.polymer.virtualentity.api.VirtualEntityUtils;
import eu.pb4.polymer.virtualentity.api.attachment.ChunkAttachment;
import eu.pb4.polymer.virtualentity.api.attachment.HolderAttachment;
import eu.pb4.polymer.virtualentity.impl.HolderAttachmentHolder;
import java.util.ArrayList;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;

public final class PolymerHolderRangeCommand implements ModInitializer {
    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
                dispatcher.register(Commands.literal("createfly")
                        .then(Commands.literal("holders")
                                .then(Commands.literal("census").executes(context -> census(context.getSource())))
                                .then(Commands.literal("gate")
                                        .then(Commands.literal("on")
                                                .executes(context -> gate(context.getSource(), true)))
                                        .then(Commands.literal("off")
                                                .executes(context -> gate(context.getSource(), false)))))));
    }

    private static int census(CommandSourceStack source) {
        int count = 0;
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            String line = player.getName().getString() + ": " + census(player);
            source.sendSuccess(() -> Component.literal(line), false);
            count++;
        }
        return count;
    }

    private static int gate(CommandSourceStack source, boolean on) {
        PolymerHolderRange.ENABLED = on;
        source.sendSuccess(() -> Component.literal("Block holder distance gate " + (on ? "on" : "off")), true);
        return 1;
    }

    private static String census(ServerPlayer player) {
        ServerLevel level = player.level();
        int radius = player.level().getServer().getPlayerList().getViewDistance() + 1;
        int cx = player.chunkPosition().x(), cz = player.chunkPosition().z();
        long[] watched = new long[3], held = new long[3];
        long holdersWatched = 0, holdersTotal = 0;
        for (int x = cx - radius; x <= cx + radius; x++)
            for (int z = cz - radius; z <= cz + radius; z++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
                if (!(chunk instanceof HolderAttachmentHolder holders)
                        || !VirtualEntityUtils.isPlayerTracking(player, chunk)) continue;
                for (HolderAttachment attachment : new ArrayList<>(holders.polymerVE$getHolders())) {
                    if (!(attachment instanceof ChunkAttachment) || !(attachment.holder() instanceof AdapterHolder h))
                        continue;
                    double d = Math.sqrt(attachment.getPos().distanceToSqr(player.position()));
                    int band = d < 64 ? 0 : d <= PolymerHolderRange.START ? 1 : 2;
                    int elements = h.getElements().size();
                    boolean on = h.getWatchingPlayers().contains(player.connection);
                    held[band] += elements;
                    holdersTotal++;
                    if (on) {
                        watched[band] += elements;
                        holdersWatched++;
                    }
                }
            }
        return String.format(
                "gate %s; chunk holders watched %d/%d; elements watched/present: <64 m %d/%d, 64-%d m %d/%d, >%d m %d/%d",
                PolymerHolderRange.ENABLED ? "on" : "off",
                holdersWatched,
                holdersTotal,
                watched[0],
                held[0],
                (int) PolymerHolderRange.START,
                watched[1],
                held[1],
                (int) PolymerHolderRange.START,
                watched[2],
                held[2]);
    }
}
