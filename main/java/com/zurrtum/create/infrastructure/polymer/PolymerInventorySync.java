package com.zurrtum.create.infrastructure.polymer;

import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import eu.pb4.sgui.api.SguiUtils;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class PolymerInventorySync {
    private static final Set<UUID> PENDING = new HashSet<>();
    private static boolean registered;

    public static void register() {
        if (registered) return;
        registered = true;
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> PENDING.clear());
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PENDING.remove(handler.player.getUUID()));
        ServerTickEvents.END_SERVER_TICK.register(PolymerInventorySync::flush);
    }

    static void flush(MinecraftServer server) {
        for (var iterator = PENDING.iterator(); iterator.hasNext(); ) {
            ServerPlayer player = NativePresentationPolicy.presentationPlayer(server.getPlayerList(), iterator.next());
            if (player == null) {
                iterator.remove();
                continue;
            }
            // onRemoved runs before the native menu is restored. Also defer across menu-to-menu transitions: real
            // slots would corrupt SGUI.
            if (SguiUtils.getCurrentGui(player) != null) continue;
            player.inventoryMenu.sendAllDataToRemote();
            if (player.containerMenu != player.inventoryMenu) player.containerMenu.sendAllDataToRemote();
            iterator.remove();
        }
    }

    public static void afterMenu(ServerPlayer player) {
        if (!NativePresentationPolicy.allowsPresentation(player)) return;
        if (!player.level().getServer().isSameThread()) {
            player.level().getServer().execute(() -> afterMenu(player));
            return;
        }
        PENDING.add(player.getUUID());
    }
}
