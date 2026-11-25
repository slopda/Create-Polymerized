package dev.veeda.createpoly.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.zurrtum.create.content.redstone.link.controller.LinkedControllerItem;
import com.zurrtum.create.content.redstone.link.controller.LinkedControllerServerHandler;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerGhostItemGui;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = LinkedControllerItem.class, remap = false)
abstract class LinkedControllerMenuHookMixin {
    @WrapOperation(
            method = "use",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/zurrtum/create/content/redstone/link/controller/LinkedControllerItem;openHandledScreen(Lnet/minecraft/server/level/ServerPlayer;)V"))
    private void polymerpatch$openController(LinkedControllerItem item, ServerPlayer player, Operation<Void> original) {
        if (PolymerNativeClients.isNative(player)) {
            original.call(item, player);
            return;
        }
        LinkedControllerServerHandler.releasePersistentInputs(player.level(), player.blockPosition(), player.getUUID());
        PolymerGhostItemGui.openController(player);
    }
}
