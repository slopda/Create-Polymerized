package com.zurrtum.create.infrastructure.polymer.train;

import com.zurrtum.create.content.contraptions.AbstractContraptionEntity;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerSchematicGuis;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class PolymerTrainMessages implements ModInitializer {
    private static final Set<UUID> ACTIVE = new HashSet<>();

    public static Component started(ServerPlayer player, AbstractContraptionEntity entity) {
        if (!PolymerNativeClients.isNative(player)) ACTIVE.add(player.getUUID());
        return Component.translatableWithFallback(
                "create.contraption.controls.start_controlling",
                PolymerSchematicGuis.lang("create.contraption.controls.start_controlling"),
                entity.getContraptionName());
    }

    @Override
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(PolymerTrainMessages::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ACTIVE.remove(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> ACTIVE.clear());
    }

    static void tick(MinecraftServer server) {
        for (var p : NativePresentationPolicy.onlinePlayers(server.getPlayerList())) {
            if (PolymerNativeClients.isNative(p)) {
                ACTIVE.remove(p.getUUID());
                continue;
            }
            if (PolymerTrainControls.drivenEntity(p) != null) continue;
            if (ACTIVE.remove(p.getUUID()))
                p.sendOverlayMessage(Component.translatableWithFallback(
                        "create.contraption.controls.stop_controlling",
                        PolymerSchematicGuis.lang("create.contraption.controls.stop_controlling")));
        }
    }
}
