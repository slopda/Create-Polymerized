package dev.veeda.createpoly.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerMenuArt;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ServerPlayer.class)
abstract class MenuTitleArtMixin {
    @ModifyArg(
            method = "openMenu",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/network/protocol/game/ClientboundOpenScreenPacket;<init>(ILnet/minecraft/world/inventory/MenuType;Lnet/minecraft/network/chat/Component;)V"),
            index = 2)
    private Component polymerpatch$menuArt(Component title, @Local AbstractContainerMenu menu) {
        if (PolymerNativeClients.isNative((ServerPlayer) (Object) this)) return title;
        return PolymerMenuArt.title(menu, title);
    }
}
