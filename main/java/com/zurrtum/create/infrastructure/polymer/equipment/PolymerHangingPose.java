package com.zurrtum.create.infrastructure.polymer.equipment;

import com.mojang.datafixers.util.Pair;
import com.zurrtum.create.AllItemTags;
import com.zurrtum.create.content.kinetics.chainConveyor.ServerChainConveyorHandler;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.CustomData;
import org.jspecify.annotations.Nullable;

public final class PolymerHangingPose {
    private static final String MARKER = "createpoly_hanging";
    private static final Consumable POSE = Consumable.builder()
            .consumeSeconds(3600)
            .animation(ItemUseAnimation.TRIDENT)
            .sound(Holder.direct(SoundEvents.EMPTY))
            .hasConsumeParticles(false)
            .build();

    private static final Map<UUID, EquipmentSlot> POSED = new HashMap<>();

    private PolymerHangingPose() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(PolymerHangingPose::tick);
    }

    public static void project(ItemStack original, ItemStack client) {
        CustomData data = original.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.copyTag().contains(MARKER)) return;
        client.set(DataComponents.CONSUMABLE, POSE);
        client.remove(DataComponents.CUSTOM_DATA);
    }

    private static void tick(MinecraftServer server) {
        Set<UUID> online = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            online.add(id);
            InteractionHand hand = ServerChainConveyorHandler.hangingPlayers.containsKey(id) ? hand(player) : null;
            EquipmentSlot previous = POSED.remove(id);
            if (hand != null) {
                EquipmentSlot slot = hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
                if (previous != null && previous != slot) restoreEquipment(player, previous);
                POSED.put(id, slot);
                ItemStack marked = player.getItemBySlot(slot).copy();
                CustomData.update(DataComponents.CUSTOM_DATA, marked, tag -> tag.putBoolean(MARKER, true));
                send(player, new ClientboundSetEquipmentPacket(player.getId(), List.of(Pair.of(slot, marked))));
                send(player, flags(player, (byte) (1 | (hand == InteractionHand.OFF_HAND ? 2 : 0))));
            } else if (previous != null) {
                restoreEquipment(player, previous);
                send(player, flags(player, player.getEntityData().get(LivingEntity.DATA_LIVING_ENTITY_FLAGS)));
            }
        }
        POSED.keySet().retainAll(online);
    }

    private static @Nullable InteractionHand hand(ServerPlayer player) {
        if (player.getMainHandItem().is(AllItemTags.CHAIN_RIDEABLE)) return InteractionHand.MAIN_HAND;
        if (player.getOffhandItem().is(AllItemTags.CHAIN_RIDEABLE)) return InteractionHand.OFF_HAND;
        return null;
    }

    private static void restoreEquipment(ServerPlayer player, EquipmentSlot slot) {
        send(
                player,
                new ClientboundSetEquipmentPacket(
                        player.getId(),
                        List.of(Pair.of(slot, player.getItemBySlot(slot).copy()))));
    }

    private static ClientboundSetEntityDataPacket flags(ServerPlayer player, byte flags) {
        return new ClientboundSetEntityDataPacket(
                player.getId(),
                List.of(SynchedEntityData.DataValue.create(LivingEntity.DATA_LIVING_ENTITY_FLAGS, flags)));
    }

    private static void send(ServerPlayer player, Packet<? super ClientGamePacketListener> packet) {
        player.level().getChunkSource().sendToTrackingPlayers(player, packet);
    }
}
