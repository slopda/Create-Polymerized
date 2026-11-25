package dev.veeda.createpoly.mixin;

import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerList.class)
public abstract class NativeAdapterPlayersMixin {
    @Inject(method = "getPlayers", at = @At("RETURN"), cancellable = true)
    private void adapterPlayers(CallbackInfoReturnable<List<ServerPlayer>> cir) {
        if (NativePresentationPolicy.adapterPlayerLookup())
            cir.setReturnValue(cir.getReturnValue().stream()
                    .filter(player -> !PolymerNativeClients.isNative(player))
                    .toList());
    }

    @Inject(
            method = "getPlayer(Ljava/util/UUID;)Lnet/minecraft/server/level/ServerPlayer;",
            at = @At("RETURN"),
            cancellable = true)
    private void adapterPlayer(UUID id, CallbackInfoReturnable<ServerPlayer> cir) {
        if (PolymerNativeClients.isNative(cir.getReturnValue()) && NativePresentationPolicy.adapterPlayerLookup())
            cir.setReturnValue(null);
    }
}
