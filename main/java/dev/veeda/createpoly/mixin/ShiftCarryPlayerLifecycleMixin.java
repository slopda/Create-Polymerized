package dev.veeda.createpoly.mixin;

import com.zurrtum.create.infrastructure.polymer.contraption.PolymerShiftCarry;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ShiftCarryPlayerLifecycleMixin {
    @Inject(
            method =
                    "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;",
            at = @At("HEAD"))
    private void cancelTransition(CallbackInfoReturnable<ServerPlayer> ci) {
        if ((Object) this instanceof ServerPlayer player) PolymerShiftCarry.cancelPending(player);
    }

    @Inject(
            method = {"setServerLevel", "disconnect", "restoreFrom"},
            at = @At("HEAD"))
    private void cancelLifecycle(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player) PolymerShiftCarry.cancelPending(player);
    }
}
