package dev.veeda.createpoly.mixin;

import com.zurrtum.create.infrastructure.polymer.contraption.PolymerShiftCarry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ShiftCarryTeleportMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "teleport", at = @At("HEAD"))
    private void cancelShiftCarry(CallbackInfo ci) {
        PolymerShiftCarry.cancelPending(player);
    }
}
