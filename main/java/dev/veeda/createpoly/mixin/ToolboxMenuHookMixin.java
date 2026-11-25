package dev.veeda.createpoly.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.zurrtum.create.content.equipment.toolbox.ToolboxBlock;
import com.zurrtum.create.content.equipment.toolbox.ToolboxBlockEntity;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerToolboxGui;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ToolboxBlock.class, remap = false)
abstract class ToolboxMenuHookMixin {
    @WrapOperation(
            method = "/^lambda\\$useItemOn\\$\\d+$/",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/zurrtum/create/content/equipment/toolbox/ToolboxBlockEntity;openHandledScreen(Lnet/minecraft/server/level/ServerPlayer;)V"))
    private static void polymerpatch$openToolbox(
            ToolboxBlockEntity toolbox, ServerPlayer player, Operation<Void> original) {
        if (PolymerNativeClients.isNative(player)) {
            original.call(toolbox, player);
            return;
        }
        if (player.isSpectator() || !player.mayInteract(player.level(), toolbox.getBlockPos())) return;
        PolymerToolboxGui.open(player, toolbox);
    }
}
