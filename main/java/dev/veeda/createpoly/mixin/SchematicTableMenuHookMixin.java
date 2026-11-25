package dev.veeda.createpoly.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.zurrtum.create.content.schematics.table.SchematicTableBlock;
import com.zurrtum.create.content.schematics.table.SchematicTableBlockEntity;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import com.zurrtum.create.infrastructure.polymer.schematic.PolymerSchematicMenus;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = SchematicTableBlock.class, remap = false)
abstract class SchematicTableMenuHookMixin {
    @WrapOperation(
            method = "/^lambda\\$useWithoutItem\\$\\d+$/",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/zurrtum/create/content/schematics/table/SchematicTableBlockEntity;openHandledScreen(Lnet/minecraft/server/level/ServerPlayer;)V"))
    private static void polymerpatch$openTable(
            SchematicTableBlockEntity self, ServerPlayer player, Operation<Void> original) {
        if (PolymerNativeClients.isNative(player)) {
            original.call(self, player);
            return;
        }
        if (player.isSpectator() || !player.mayBuild() || !player.mayInteract(player.level(), self.getBlockPos()))
            return;
        PolymerSchematicMenus.openTable(player, self);
    }
}
