package com.zurrtum.create.infrastructure.polymer.equipment;

import com.mojang.datafixers.util.Pair;
import com.zurrtum.create.AllPackageStyles;
import com.zurrtum.create.Create;
import com.zurrtum.create.content.equipment.armor.CardboardArmorHandler;
import com.zurrtum.create.content.equipment.armor.CardboardArmorItem;
import com.zurrtum.create.infrastructure.polymer.AdapterHolder;
import com.zurrtum.create.infrastructure.polymer.PolymerDisplays;
import com.zurrtum.create.infrastructure.polymer.block.PolymerKineticVisuals;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import dev.veeda.createpoly.assets.CreateIds;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ManualAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.mixin.accessors.EntityAccessor;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class PolymerCardboardDisguise {
    private static final Map<ServerPlayer, Box> BOXES = new IdentityHashMap<>();

    private record Snapshot(Player owner, boolean active, byte sharedFlags) {}

    private static final Map<UUID, Snapshot> SNAPSHOTS = new ConcurrentHashMap<>();

    public static void register() {
        PolymerEntityUtils.registerPolymerEntityConstructor(EntityTypes.PLAYER, Projection::new);
        PolymerItemUtils.ITEM_MODIFICATION_EVENT.register((original, client, context) -> {
            if (!(original.getItem() instanceof CardboardArmorItem)) return client;
            var connection = context.get(PacketContext.CONNECTION);
            var profile = context.get(PacketContext.GAME_PROFILE);
            UUID viewer = connection != null
                            && connection.getPacketListener() instanceof ServerGamePacketListenerImpl listener
                    ? listener.getPlayer().getUUID()
                    : profile != null ? profile.id() : null;
            projectItem(original, client, viewer);
            return client;
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> publish(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SNAPSHOTS.computeIfPresent(
                handler.player.getUUID(), (id, snapshot) -> snapshot.owner() == handler.player ? null : snapshot));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            List<ServerPlayer> online = new ArrayList<>();
            for (var level : server.getAllLevels()) online.addAll(level.players());
            for (ServerPlayer player : online) publish(player);
            for (ServerPlayer player : List.copyOf(BOXES.keySet())) {
                if (!online.contains(player) || !active(player) || BOXES.get(player).level != player.level()) {
                    BOXES.remove(player).holder.destroy();
                    if (!player.isRemoved() && player.connection.isAcceptingMessages()) refresh(player);
                }
            }
            for (ServerPlayer player : online) {
                if (!active(player)) continue;
                Box box = BOXES.get(player);
                if (box == null) {
                    box = new Box(player);
                    BOXES.put(player, box);
                    refresh(player);
                }
                box.update(player);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            BOXES.values().forEach(box -> box.holder.destroy());
            BOXES.clear();
            SNAPSHOTS.clear();
        });
    }

    static boolean active(Player player) {
        return player.isAlive() && !player.isSpectator() && CardboardArmorHandler.testForStealth(player);
    }

    private static void publish(ServerPlayer player) {
        if (!player.level().getServer().isSameThread())
            throw new IllegalStateException("Cardboard snapshots require the server thread");
        boolean active = active(player);
        byte flags = player.getEntityData().get(EntityAccessor.getDATA_SHARED_FLAGS_ID());
        Snapshot previous = SNAPSHOTS.get(player.getUUID());
        if (previous != null
                && previous.owner() == player
                && previous.active() == active
                && previous.sharedFlags() == flags) return;
        SNAPSHOTS.put(player.getUUID(), new Snapshot(player, active, flags));
    }

    private static Snapshot snapshot(Player player) {
        Snapshot value = SNAPSHOTS.get(player.getUUID());
        return value != null && value.owner() == player ? value : null;
    }

    private static byte flags(Player player) {
        byte flags = player.getEntityData().get(EntityAccessor.getDATA_SHARED_FLAGS_ID());
        return active(player) ? (byte) (flags | 1 << EntityAccessor.getFLAG_INVISIBLE()) : flags;
    }

    private static void refresh(ServerPlayer player) {
        var metadata = new ClientboundSetEntityDataPacket(
                player.getId(),
                List.of(SynchedEntityData.DataValue.create(EntityAccessor.getDATA_SHARED_FLAGS_ID(), flags(player))));
        var equipment = new ArrayList<Pair<EquipmentSlot, ItemStack>>();
        for (EquipmentSlot slot : EquipmentSlot.values())
            equipment.add(Pair.of(slot, player.getItemBySlot(slot).copy()));
        var viewers = new ArrayList<>(PlayerLookup.tracking(player));
        if (!viewers.contains(player)) viewers.add(player);
        for (ServerPlayer viewer : viewers) {
            NativePresentationPolicy.sendPresentation(viewer, PolymerEntityUtils.setEntityContext(metadata, player));
            NativePresentationPolicy.sendPresentation(
                    viewer,
                    PolymerEntityUtils.setEntityContext(
                            new ClientboundSetEquipmentPacket(player.getId(), equipment), player));
        }
        if (!PolymerNativeClients.isNative(player)) {
            player.inventoryMenu.sendAllDataToRemote();
            if (player.containerMenu != player.inventoryMenu) player.containerMenu.sendAllDataToRemote();
        }
    }

    private record Projection(Player player) implements PolymerEntity {
        @Override
        public EntityType<?> getPolymerEntityType(PacketContext context) {
            return EntityTypes.PLAYER;
        }

        @Override
        public void modifyRawTrackedData(
                List<SynchedEntityData.DataValue<?>> data, ServerPlayer viewer, boolean initial) {
            if (PolymerNativeClients.isNative(viewer)) return;
            Snapshot snapshot = snapshot(player);
            if (snapshot == null || !snapshot.active()) return;
            int id = EntityAccessor.getDATA_SHARED_FLAGS_ID().id();
            byte flags = snapshot.sharedFlags();
            for (var value : data) if (value.id() == id && value.value() instanceof Byte original) flags = original;
            data.removeIf(value -> value.id() == id);
            data.add(SynchedEntityData.DataValue.create(EntityAccessor.getDATA_SHARED_FLAGS_ID(), (byte)
                    (flags | 1 << EntityAccessor.getFLAG_INVISIBLE())));
        }

        @Override
        public List<Pair<EquipmentSlot, ItemStack>> getPolymerVisibleEquipment(
                List<Pair<EquipmentSlot, ItemStack>> equipment, ServerPlayer viewer) {
            Snapshot snapshot = snapshot(player);
            return snapshot != null && snapshot.active() && viewer != player && !PolymerNativeClients.isNative(viewer)
                    ? equipment.stream()
                            .map(pair -> Pair.of(pair.getFirst(), ItemStack.EMPTY))
                            .toList()
                    : equipment;
        }
    }

    private static volatile boolean loggedHide, loggedMissingViewer;
    private static final ResourceKey<EquipmentAsset> HIDDEN_ASSET =
            ResourceKey.create(EquipmentAssets.ROOT_ID, CreateIds.id("polymer_hidden"));

    public static void missingViewer(ItemStack original) {
        if (loggedMissingViewer || !(original.getItem() instanceof CardboardArmorItem)) return;
        loggedMissingViewer = true;
        Create.LOGGER.warn(
                "Cardboard armor encoded without a viewer in the packet context; disguise projection skipped");
    }

    public static void projectItem(
            ItemStack original, ItemStack client, @org.jspecify.annotations.Nullable UUID viewer) {
        if (!(original.getItem() instanceof CardboardArmorItem)) return;
        var eq = original.get(DataComponents.EQUIPPABLE);
        if (eq == null) return;
        Snapshot snapshot = viewer == null ? null : SNAPSHOTS.get(viewer);
        boolean hiding = snapshot != null && snapshot.active();
        if (!loggedHide) {
            loggedHide = true;
            Create.LOGGER.info("Cardboard armor projection active (viewer {}, disguised {})", viewer, hiding);
        }
        client.set(
                DataComponents.EQUIPPABLE,
                new Equippable(
                        eq.slot(),
                        eq.equipSound(),
                        hiding ? Optional.of(HIDDEN_ASSET) : eq.assetId(),
                        hiding ? eq.cameraOverlay() : Optional.empty(),
                        eq.allowedEntities(),
                        eq.dispensable(),
                        eq.swappable(),
                        eq.damageOnHurt(),
                        eq.equipOnInteract(),
                        eq.canBeSheared(),
                        eq.shearingSound()));
    }

    private static final class Box {
        final ElementHolder holder = new AdapterHolder();
        final ItemDisplayElement display;
        final ServerLevel level;

        Box(ServerPlayer player) {
            level = player.level();
            var styles =
                    AllPackageStyles.ALL.stream().filter(style -> !style.rare()).toList();
            var style = styles.get(ThreadLocalRandom.current().nextInt(styles.size()));
            display = PolymerDisplays.item(
                    PolymerKineticVisuals.rotatingDisplayItem(style.getItemId()), ItemDisplayContext.NONE);
            display.setRightRotation(new Quaternionf().rotationY((float) Math.PI));
            display.setTeleportDuration(1);
            display.setInterpolationDuration(1);
            update(player);
            holder.addElement(display);
            new ManualAttachment(holder, level, player::position);
            syncViewers(player);
        }

        private void syncViewers(ServerPlayer player) {
            List<ServerPlayer> viewers = new ArrayList<>(PlayerLookup.tracking(player));
            if (!viewers.contains(player)) viewers.add(player);
            for (var listener : List.copyOf(holder.getWatchingPlayers()))
                if (!viewers.contains(listener.getPlayer())) holder.stopWatching(listener);
            for (ServerPlayer viewer : viewers)
                if (!PolymerNativeClients.isNative(viewer)
                        && !viewer.hasDisconnected()
                        && !holder.getWatchingPlayers().contains(viewer.connection)) holder.startWatching(viewer);
        }

        void update(ServerPlayer player) {
            float scale = player.getScale();
            display.setScale(new Vector3f(scale));
            display.setOffset(new Vec3(0, .5 * scale, 0));
            display.setYaw(player.getYRot());
            if (display.getHolder() != null) {
                syncViewers(player);
                holder.tick();
            }
        }
    }
}
