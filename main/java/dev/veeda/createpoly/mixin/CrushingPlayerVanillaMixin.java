package dev.veeda.createpoly.mixin;

import com.zurrtum.create.content.kinetics.crusher.CrushingWheelControllerBlockEntity;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CrushingWheelControllerBlockEntity.class)
public abstract class CrushingPlayerVanillaMixin {
    @Inject(method = "tick", at = @At("RETURN"))
    private void polymerPullVanillaPlayer(CallbackInfo ci) {
        var self = (CrushingWheelControllerBlockEntity) (Object) this;
        if (self.processingEntity instanceof ServerPlayer player
                && !PolymerNativeClients.isNative(player)
                && player.isAlive()) player.hurtMarked = true;
    }
}
