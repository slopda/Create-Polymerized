package com.zurrtum.create.infrastructure.polymer;

import com.mojang.datafixers.util.Pair;
import com.zurrtum.create.AllEntityTypes;
import com.zurrtum.create.Create;
import com.zurrtum.create.content.contraptions.actors.seat.SeatEntity;
import com.zurrtum.create.content.equipment.potatoCannon.PotatoProjectileEntity;
import com.zurrtum.create.content.logistics.box.PackageEntity;
import dev.veeda.createpoly.mixin.ThrowableItemProjectileAccessor;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import eu.pb4.polymer.virtualentity.mixin.accessors.InteractionAccessor;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;

final class PolymerEntityProjections {
    private PolymerEntityProjections() {}

    static void registerEntities() {
        PolymerEntityUtils.registerOverlay(AllEntityTypes.EJECTOR_ITEM, entity -> vanillaItemEntity());
        PolymerEntityUtils.registerOverlay(AllEntityTypes.PACKAGE, PolymerEntityProjections::packageEntityProjection);
        PolymerEntityUtils.registerOverlay(
                AllEntityTypes.POTATO_PROJECTILE, PolymerEntityProjections::potatoProjectileProjection);
        PolymerEntityUtils.registerOverlay(AllEntityTypes.SEAT, PolymerEntityProjections::seatEntityProjection);
        PolymerEntityUtils.registerOverlay(
                AllEntityTypes.ORIENTED_CONTRAPTION, PolymerEntityProjections::invisibleMarkerProjection);
        PolymerEntityUtils.registerOverlay(
                AllEntityTypes.CONTROLLED_CONTRAPTION, PolymerEntityProjections::invisibleMarkerProjection);
        PolymerEntityUtils.registerOverlay(
                AllEntityTypes.CARRIAGE_CONTRAPTION, PolymerEntityProjections::invisibleMarkerProjection);
        PolymerEntityUtils.registerOverlay(
                AllEntityTypes.GANTRY_CONTRAPTION, PolymerEntityProjections::invisibleMarkerProjection);
        PolymerEntityUtils.registerOverlay(
                AllEntityTypes.SUPER_GLUE, PolymerEntityProjections::invisibleMarkerProjection);
        PolymerEntityUtils.registerOverlay(
                AllEntityTypes.CRAFTING_BLUEPRINT, PolymerEntityProjections::invisibleMarkerProjection);
        Create.LOGGER.info("Registered vanilla transport entity mappings for all 10 Create entity types");
    }

    private static PolymerEntity vanillaItemEntity() {
        return context -> EntityTypes.ITEM;
    }

    private static PolymerEntity packageEntityProjection(PackageEntity entity) {
        return new PolymerEntity() {
            @Override
            public EntityType<?> getPolymerEntityType(PacketContext context) {
                return EntityTypes.INTERACTION;
            }

            @Override
            public void modifyRawTrackedData(
                    List<SynchedEntityData.DataValue<?>> data, ServerPlayer player, boolean initial) {
                data.clear();
                data.add(SynchedEntityData.DataValue.create(
                        InteractionAccessor.getDATA_WIDTH_ID(), entity.getBbWidth()));
                data.add(SynchedEntityData.DataValue.create(
                        InteractionAccessor.getDATA_HEIGHT_ID(), entity.getBbHeight()));
                data.add(SynchedEntityData.DataValue.create(InteractionAccessor.getDATA_RESPONSE_ID(), true));
            }

            @Override
            public List<Pair<EquipmentSlot, ItemStack>> getPolymerVisibleEquipment(
                    List<Pair<EquipmentSlot, ItemStack>> items, ServerPlayer player) {
                return List.of();
            }
        };
    }

    private static PolymerEntity potatoProjectileProjection(PotatoProjectileEntity entity) {
        return new PolymerEntity() {
            @Override
            public EntityType<?> getPolymerEntityType(PacketContext context) {
                return EntityTypes.SNOWBALL;
            }

            @Override
            public void modifyRawTrackedData(
                    List<SynchedEntityData.DataValue<?>> data, ServerPlayer player, boolean initial) {
                data.clear();
                if (initial && !entity.getItem().isEmpty())
                    data.add(SynchedEntityData.DataValue.create(
                            ThrowableItemProjectileAccessor.polymerpatch$itemStack(),
                            entity.getItem().copyWithCount(1)));
            }
        };
    }

    private static PolymerEntity seatEntityProjection(SeatEntity entity) {
        return new PolymerEntity() {
            @Override
            public EntityType<?> getPolymerEntityType(PacketContext context) {
                return EntityTypes.INTERACTION;
            }

            @Override
            public void modifyRawTrackedData(
                    List<SynchedEntityData.DataValue<?>> data, ServerPlayer player, boolean initial) {
                data.clear();
                data.add(SynchedEntityData.DataValue.create(InteractionAccessor.getDATA_WIDTH_ID(), 0f));
                data.add(SynchedEntityData.DataValue.create(InteractionAccessor.getDATA_HEIGHT_ID(), .35f + 1f / 16));
                data.add(SynchedEntityData.DataValue.create(InteractionAccessor.getDATA_RESPONSE_ID(), false));
            }
        };
    }

    private static PolymerEntity invisibleMarkerProjection(Entity entity) {
        return new PolymerEntity() {
            @Override
            public EntityType<?> getPolymerEntityType(PacketContext context) {
                return EntityTypes.ARMOR_STAND;
            }

            @Override
            public void modifyRawTrackedData(
                    List<SynchedEntityData.DataValue<?>> data, ServerPlayer player, boolean initial) {
                data.clear();
                ArmorStand projection = new ArmorStand(EntityTypes.ARMOR_STAND, entity.level());
                projection.setInvisible(true);
                projection.getEntityData().set(ArmorStand.DATA_CLIENT_FLAGS, (byte)
                        (ArmorStand.CLIENT_FLAG_MARKER | ArmorStand.CLIENT_FLAG_NO_BASEPLATE));
                projection.setNoGravity(true);
                data.addAll(projection.getEntityData().getNonDefaultValues());
            }
        };
    }
}
