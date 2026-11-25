package dev.veeda.createpoly.mixin;

import com.zurrtum.create.infrastructure.polymer.contraption.PolymerShiftCarry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ShiftCarrySpeedCheckMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "shouldCheckPlayerMovement", at = @At("HEAD"), cancellable = true)
    private void exemptCarriedRider(boolean fallFlying, CallbackInfoReturnable<Boolean> cir) {
        if (!fallFlying && PolymerShiftCarry.exemptFromSpeedCheck(player)) cir.setReturnValue(false);
    }
}
