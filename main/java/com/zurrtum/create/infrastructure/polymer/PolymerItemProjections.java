package com.zurrtum.create.infrastructure.polymer;

import com.zurrtum.create.AllItemTags;
import com.zurrtum.create.Create;
import com.zurrtum.create.content.equipment.armor.BacktankItem;
import com.zurrtum.create.content.equipment.armor.BacktankUtil;
import com.zurrtum.create.content.equipment.sandPaper.SandPaperItem;
import com.zurrtum.create.content.logistics.box.PackageItem;
import com.zurrtum.create.infrastructure.polymer.equipment.PolymerCardboardDisguise;
import com.zurrtum.create.infrastructure.polymer.equipment.PolymerHangingPose;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import com.zurrtum.create.infrastructure.polymer.ponder.PolymerPonderEntry;
import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.block.entity.BlockEntityType;

final class PolymerItemProjections {
    private static final PolymerItem CREATE_ITEM = new PolymerItem() {
        @Override
        public ItemStack getPolymerItemStack(
                ItemStack itemStack, TooltipFlag tooltipType, PacketContext context, HolderLookup.Provider lookup) {
            if (PolymerNativeClients.isNative(context)) return itemStack;
            return PolymerItem.super.getPolymerItemStack(itemStack, tooltipType, context, lookup);
        }

        @Override
        public boolean canSyncRawToClient(PacketContext context) {
            return PolymerNativeClients.isNative(context);
        }

        @Override
        public void modifyBasePolymerItemStack(
                ItemStack client, ItemStack original, PacketContext context, HolderLookup.Provider lookup) {
            var connection = context.get(PacketContext.CONNECTION);
            var profile = context.get(PacketContext.GAME_PROFILE);
            UUID viewer = connection != null
                            && connection.getPacketListener() instanceof ServerGamePacketListenerImpl listener
                    ? listener.getPlayer().getUUID()
                    : profile != null ? profile.id() : null;
            if (viewer != null) PolymerCardboardDisguise.projectItem(original, client, viewer);
            else PolymerCardboardDisguise.missingViewer(original);
            if (original.is(AllItemTags.CHAIN_RIDEABLE)) PolymerHangingPose.project(original, client);
            if (original.getItem() instanceof SandPaperItem)
                client.set(
                        DataComponents.CONSUMABLE,
                        Consumable.builder()
                                .consumeSeconds(32 / 20f)
                                .animation(ItemUseAnimation.BRUSH)
                                .hasConsumeParticles(false)
                                .build());
            if (original.getItem() instanceof BacktankItem) backtankBar(original, client);
            if (!PolymerNativeClients.isNative(context)) PolymerPonderEntry.tooltipStyle(original, client, viewer);
        }

        @Override
        public void modifyClientTooltip(List<Component> tooltip, ItemStack stack, PacketContext context) {
            if (!PolymerNativeClients.isNative(context)) PolymerPonderEntry.tooltip(tooltip, stack, viewer(context));
        }

        private static void backtankBar(ItemStack original, ItemStack client) {
            int max = BacktankUtil.maxAir(original);
            if (max <= 0) return;
            int air = Math.max(0, Math.min(max, BacktankUtil.getAir(original)));
            client.set(DataComponents.MAX_STACK_SIZE, 1);
            client.set(DataComponents.MAX_DAMAGE, max);
            client.set(DataComponents.DAMAGE, max - air);
        }

        private UUID viewer(PacketContext context) {
            var connection = context.get(PacketContext.CONNECTION);
            var profile = context.get(PacketContext.GAME_PROFILE);
            return connection != null && connection.getPacketListener() instanceof ServerGamePacketListenerImpl listener
                    ? listener.getPlayer().getUUID()
                    : profile != null ? profile.id() : null;
        }

        @Override
        public Item getPolymerItem(ItemStack itemStack, PacketContext context) {
            if (PolymerNativeClients.isNative(context)) return itemStack.getItem();
            return itemStack.getItem() instanceof PackageItem ? Items.BOW : Items.PAPER;
        }

        @Override
        public Identifier getPolymerItemModel(
                ItemStack itemStack, PacketContext context, HolderLookup.Provider lookup) {
            Identifier stackModel = itemStack.get(DataComponents.ITEM_MODEL);
            if (stackModel != null) {
                return stackModel;
            }

            Identifier itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
            return itemId != null && itemId.getNamespace().equals(Create.MOD_ID) ? itemId : null;
        }
    };

    private PolymerItemProjections() {}

    static void registerItems() {
        int count = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (id != null && id.getNamespace().equals(Create.MOD_ID)) {
                PolymerItem.registerOverlay(item, CREATE_ITEM);
                count++;
            }
        }
        Create.LOGGER.info("Registered Polymer item representations for {} Create items", count);
    }

    static void registerBlockEntities() {
        int count = 0;
        for (BlockEntityType<?> type : BuiltInRegistries.BLOCK_ENTITY_TYPE) {
            Identifier id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
            if (id != null && id.getNamespace().equals(Create.MOD_ID)) {
                PolymerBlockUtils.registerBlockEntity(type, new PolymerSyncedObject<>() {
                    @Override
                    public BlockEntityType<?> getPolymerReplacement(
                            BlockEntityType<?> original, PacketContext context) {
                        return PolymerNativeClients.isNative(context) ? original : null;
                    }

                    @Override
                    public boolean canSyncRawToClient(PacketContext context) {
                        return PolymerNativeClients.isNative(context);
                    }
                });
                count++;
            }
        }
        Create.LOGGER.info("Registered Polymer block entity packet handling for {} Create types", count);
    }
}
