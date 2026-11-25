package com.zurrtum.create.infrastructure.polymer.contraption;

import com.zurrtum.create.AllItems;
import com.zurrtum.create.content.contraptions.AbstractContraptionEntity;
import com.zurrtum.create.content.contraptions.minecart.CouplingHandler;
import com.zurrtum.create.content.contraptions.minecart.capability.CapabilityMinecartController;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.phys.AABB;

public final class PolymerMinecartInteractions {
    private static final Map<UUID, AbstractMinecart> SELECTED = new HashMap<>();

    public static void register() {
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> SELECTED.clear());
        ServerTickEvents.END_SERVER_TICK.register(server -> SELECTED.entrySet().removeIf(entry -> {
            ServerPlayer player = NativePresentationPolicy.presentationPlayer(server.getPlayerList(), entry.getKey());
            AbstractMinecart cart = entry.getValue();
            return player == null
                    || !player.isAlive()
                    || player.isSpectator()
                    || cart.isRemoved()
                    || player.level() != cart.level()
                    || !holdingCoupling(player);
        }));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : NativePresentationPolicy.presentationPlayers(server.getPlayerList()))
                pushCarts(player);
        });
    }

    private static void pushCarts(ServerPlayer player) {
        if (player.isSpectator() || player.isPassenger() || !player.isAlive()) return;
        AABB body = player.getBoundingBox();
        for (AbstractContraptionEntity contraption :
                player.level().getEntitiesOfClass(AbstractContraptionEntity.class, body.inflate(3))) {
            if (!(contraption.getVehicle() instanceof AbstractMinecart cart) || cart.isRemoved()) continue;
            AABB base = cart.getBoundingBox();
            if (body.intersects(base)) continue;
            AABB column = new AABB(
                            base.minX,
                            base.minY,
                            base.minZ,
                            base.maxX,
                            Math.max(base.maxY, contraption.getBoundingBox().maxY),
                            base.maxZ)
                    .inflate(.1, 0, .1);
            if (body.intersects(column)) cart.push(player);
        }
    }

    private static boolean holdingCoupling(Player player) {
        return player.getMainHandItem().is(AllItems.MINECART_COUPLING)
                || player.getOffhandItem().is(AllItems.MINECART_COUPLING);
    }

    public static Entity cartTarget(Entity entity) {
        return entity instanceof AbstractContraptionEntity && entity.getVehicle() instanceof AbstractMinecart cart
                ? cart
                : entity;
    }

    public static boolean couple(Player player, InteractionHand hand, Entity target) {
        if (!(player instanceof ServerPlayer serverPlayer)
                || !player.getItemInHand(hand).is(AllItems.MINECART_COUPLING)) return false;
        Entity resolved = cartTarget(target);
        if (!(resolved instanceof AbstractMinecart cart)
                || cart.isRemoved()
                || !player.isAlive()
                || player.isSpectator()
                || cart.level() != player.level()) return false;
        var controller = CapabilityMinecartController.getIfPresent(cart.level(), cart.getUUID());
        if (controller == null || !controller.isPresent()) return false;
        if (controller.isFullyCoupled()) {
            CouplingHandler.status(player, "two_couplings_max");
            return true;
        }
        AbstractMinecart first = SELECTED.get(player.getUUID());
        if (first == null || first == cart || first.isRemoved() || first.level() != cart.level()) {
            SELECTED.put(player.getUUID(), cart);
            serverPlayer.sendOverlayMessage(Component.literal("Cart selected — click another cart to couple"));
            return true;
        }
        SELECTED.remove(player.getUUID());
        if (CouplingHandler.tryToCoupleCarts(player, cart.level(), first.getId(), cart.getId()))
            serverPlayer.sendOverlayMessage(Component.literal("Carts coupled"));
        player.getInventory().setChanged();
        return true;
    }
}
