package com.zurrtum.create.infrastructure.polymer.block;

import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

public final class PolymerWaterWheelPlacement {
    private static final int COLOR = 0xff5d6c;
    private static final int LIFETIME = 10;

    private record Shown(ElementHolder holder, ServerLevel level, AABB box, int expires) {}

    private static final Map<UUID, Shown> SHOWN = new HashMap<>();
    private static boolean registered;

    private PolymerWaterWheelPlacement() {}

    public static void showBounds(ServerPlayer player, Direction.Axis axis, BlockPos clicked) {
        if (!player.level().getServer().isSameThread() || PolymerNativeClients.isNative(player)) return;
        register();
        AABB box = new AABB(clicked)
                .inflate(1)
                .deflate(
                        axis == Direction.Axis.X ? 1 : 0,
                        axis == Direction.Axis.Y ? 1 : 0,
                        axis == Direction.Axis.Z ? 1 : 0);
        int expires = player.level().getServer().getTickCount() + LIFETIME;
        Shown previous = SHOWN.remove(player.getUUID());
        ElementHolder holder;
        if (previous != null && previous.level == player.level() && previous.box.equals(box)) holder = previous.holder;
        else {
            if (previous != null) previous.holder.destroy();
            holder = PolymerBoxOutline.show(player, box, COLOR);
        }
        SHOWN.put(player.getUUID(), new Shown(holder, player.level(), box, expires));
        player.sendOverlayMessage(Component.translatable("create.large_water_wheel.not_enough_space")
                .withColor(COLOR));
    }

    private static void register() {
        if (registered) return;
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(server -> SHOWN.entrySet().removeIf(entry -> {
            ServerPlayer player = NativePresentationPolicy.presentationPlayer(server.getPlayerList(), entry.getKey());
            boolean expired = player == null
                    || player.level() != entry.getValue().level
                    || server.getTickCount() >= entry.getValue().expires;
            if (expired) entry.getValue().holder.destroy();
            return expired;
        }));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            Shown shown = SHOWN.remove(handler.player.getUUID());
            if (shown != null) shown.holder.destroy();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            SHOWN.values().forEach(shown -> shown.holder.destroy());
            SHOWN.clear();
        });
    }
}
