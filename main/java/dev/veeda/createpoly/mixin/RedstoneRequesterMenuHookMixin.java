package dev.veeda.createpoly.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.zurrtum.create.content.logistics.redstoneRequester.RedstoneRequesterBlockEntity;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerRedstoneRequesterGui;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = RedstoneRequesterBlockEntity.class, remap = false)
abstract class RedstoneRequesterMenuHookMixin {
    @WrapOperation(
            method = "use",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/zurrtum/create/content/logistics/redstoneRequester/RedstoneRequesterBlockEntity;openHandledScreen(Lnet/minecraft/server/level/ServerPlayer;)V"))
    private void polymerpatch$openRequester(
            RedstoneRequesterBlockEntity self, ServerPlayer player, Operation<Void> original) {
        if (PolymerNativeClients.isNative(player)) original.call(self, player);
        else PolymerRedstoneRequesterGui.open(player, self);
    }
}
