package com.zurrtum.create.infrastructure.polymer.train;

import com.zurrtum.create.AllDataComponents;
import com.zurrtum.create.content.trains.track.TrackBlockItem;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class PolymerTrackCurveToggle {
    private PolymerTrackCurveToggle() {}

    private static final Set<UUID> ENABLED = new HashSet<>();
    private static boolean registered;

    public static boolean toggle(ServerPlayer player) {
        if (!(player.getMainHandItem().getItem() instanceof TrackBlockItem)) return false;
        register();
        boolean on = ENABLED.add(player.getUUID());
        if (!on) {
            ENABLED.remove(player.getUUID());
            player.getMainHandItem().remove(AllDataComponents.TRACK_EXTENDED_CURVE);
        } else {
            player.getMainHandItem().set(AllDataComponents.TRACK_EXTENDED_CURVE, true);
        }
        player.sendOverlayMessage(Component.literal("Maximized turn: ")
                .append(
                        on
                                ? Component.literal("ON").withStyle(ChatFormatting.GREEN)
                                : Component.literal("OFF").withStyle(ChatFormatting.RED))
                .append(Component.literal(" (F to toggle)").withStyle(ChatFormatting.GRAY)));
        player.inventoryMenu.sendAllDataToRemote();
        return true;
    }

    private static void register() {
        if (registered) return;
        registered = true;
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ENABLED.remove(handler.player.getUUID()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (ENABLED.isEmpty()) return;
            for (ServerPlayer player : NativePresentationPolicy.presentationPlayers(server.getPlayerList())) {
                if (!ENABLED.contains(player.getUUID())) continue;
                ItemStack held = player.getMainHandItem();
                if (held.getItem() instanceof TrackBlockItem
                        && !held.getOrDefault(AllDataComponents.TRACK_EXTENDED_CURVE, false))
                    held.set(AllDataComponents.TRACK_EXTENDED_CURVE, true);
            }
        });
    }
}
