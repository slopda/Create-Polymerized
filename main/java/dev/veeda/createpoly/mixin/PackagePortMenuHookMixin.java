package dev.veeda.createpoly.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.zurrtum.create.content.logistics.packagePort.PackagePortBlockEntity;
import com.zurrtum.create.content.logistics.packagePort.PackagePortVanillaMenu;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = PackagePortBlockEntity.class, remap = false)
abstract class PackagePortMenuHookMixin {
    @WrapOperation(
            method = "use",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/zurrtum/create/content/logistics/packagePort/PackagePortBlockEntity;openHandledScreen(Lnet/minecraft/server/level/ServerPlayer;)V"))
    private void polymerpatch$openPort(PackagePortBlockEntity self, ServerPlayer player, Operation<Void> original) {
        if (PolymerNativeClients.isNative(player)) {
            original.call(self, player);
            return;
        }
        player.openMenu(new SimpleMenuProvider(
                (menuId, playerInventory, openingPlayer) ->
                        new PackagePortVanillaMenu(menuId, playerInventory, self, openingPlayer),
                self.getBlockState().getBlock().getName()));
    }
}
