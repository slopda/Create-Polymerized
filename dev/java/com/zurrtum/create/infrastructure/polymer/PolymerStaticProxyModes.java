package com.zurrtum.create.infrastructure.polymer;

import com.zurrtum.create.infrastructure.polymer.collision.PolymerStaticProxyRange;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class PolymerStaticProxyModes implements ModInitializer {
    private static final Set<UUID> LEGACY = new HashSet<>();

    @Override
    public void onInitialize() {
        PolymerStaticProxyRange.legacyRange(player -> LEGACY.contains(player.getUUID()));
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
                dispatcher.register(Commands.literal("createfly")
                        .then(Commands.literal("staticproxies")
                                .then(Commands.literal("adaptive")
                                        .executes(context ->
                                                mode(context.getSource().getPlayerOrException(), true)))
                                .then(Commands.literal("legacy")
                                        .executes(context ->
                                                mode(context.getSource().getPlayerOrException(), false))))));
        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> LEGACY.remove(handler.getPlayer().getUUID()));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> LEGACY.clear());
    }

    static int mode(ServerPlayer player, boolean adaptive) {
        if (!player.level().getServer().isSameThread())
            throw new IllegalStateException("Proxy policy is server-thread owned");
        PolymerStaticProxyRange.forget(player);
        if (adaptive) LEGACY.remove(player.getUUID());
        else LEGACY.add(player.getUUID());
        player.sendSystemMessage(Component.literal(
                adaptive
                        ? "Stationary collision helpers: nearby/targeted (default). Moving support unchanged."
                        : "Stationary collision helpers: original 12-block range until you disconnect."));
        return 1;
    }
}
