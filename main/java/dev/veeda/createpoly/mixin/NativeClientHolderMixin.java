package dev.veeda.createpoly.mixin;

import com.zurrtum.create.infrastructure.polymer.AdapterHolder;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ElementHolder.class, remap = false)
public abstract class NativeClientHolderMixin {
    @Inject(
            method = "startWatching(Lnet/minecraft/server/network/ServerGamePacketListenerImpl;)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void polymerSkipBlockHoldersForNativeCreate(
            ServerGamePacketListenerImpl player, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof AdapterHolder) || !PolymerNativeClients.isNative(player.player)) return;
        cir.setReturnValue(false);
    }
}
