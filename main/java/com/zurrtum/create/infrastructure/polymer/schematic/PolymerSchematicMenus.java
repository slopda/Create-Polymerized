package com.zurrtum.create.infrastructure.polymer.schematic;

import com.zurrtum.create.content.schematics.cannon.SchematicannonBlockEntity;
import com.zurrtum.create.content.schematics.table.SchematicTableBlockEntity;
import com.zurrtum.create.infrastructure.polymer.AdapterInteractions;
import com.zurrtum.create.infrastructure.polymer.PolymerInventorySync;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerDialogService;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerSchematicTableGui;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerSchematicannonGui;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import java.io.IOException;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;

public final class PolymerSchematicMenus implements ModInitializer {
    @Override
    public void onInitialize() {
        Identifier phase = Identifier.fromNamespaceAndPath("createpoly", "schematic_table");
        UseBlockCallback.EVENT.addPhaseOrdering(phase, Event.DEFAULT_PHASE);
        AdapterInteractions.useBlock(phase, (player, world, hand, hit) -> {
            if (!(player instanceof ServerPlayer serverPlayer) || !(world instanceof ServerLevel level))
                return InteractionResult.PASS;
            return use(serverPlayer, level, hand, hit);
        });
        PolymerResourcePackUtils.RESOURCE_PACK_AFTER_INITIAL_CREATION_EVENT.register(builder -> {
            try {
                PolymerSchematicTableGui.addAssets(builder);
                PolymerSchematicannonGui.addAssets(builder);
            } catch (IOException error) {
                throw new IllegalStateException("Could not build schematic table assets", error);
            }
        });
    }

    public static void openTable(ServerPlayer player, SchematicTableBlockEntity table) {
        if (PolymerSchematicTableGui.open(player, table)) PolymerInventorySync.afterMenu(player);
    }

    public static void openCannon(ServerPlayer player, SchematicannonBlockEntity cannon) {
        if (PolymerSchematicannonGui.open(player, cannon)) PolymerInventorySync.afterMenu(player);
    }

    static InteractionResult use(ServerPlayer player, ServerLevel level, InteractionHand hand, BlockHitResult hit) {
        if (PolymerNativeClients.isNative(player)
                || player.isSpectator()
                || player.isShiftKeyDown()
                || hand != InteractionHand.MAIN_HAND
                || !player.mayBuild()
                || player.level() != level
                || !player.mayInteract(level, hit.getBlockPos())
                || !player.isWithinBlockInteractionRange(hit.getBlockPos(), PolymerDialogService.OPEN_REACH_BUFFER))
            return InteractionResult.PASS;
        if (level.getBlockEntity(hit.getBlockPos()) instanceof SchematicannonBlockEntity cannon) {
            if (!PolymerSchematicannonGui.open(player, cannon)) return InteractionResult.PASS;
            PolymerInventorySync.afterMenu(player);
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(hit.getBlockPos()) instanceof SchematicTableBlockEntity table))
            return InteractionResult.PASS;
        if (!PolymerSchematicTableGui.open(player, table)) return InteractionResult.PASS;
        PolymerInventorySync.afterMenu(player);
        return InteractionResult.SUCCESS;
    }
}
