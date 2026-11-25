package dev.veeda.createpoly.mixin;

import com.zurrtum.create.content.logistics.tunnel.BeltTunnelBlockEntity;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BeltTunnelBlockEntity.class, remap = false)
abstract class BeltTunnelFlapHookMixin {
    @Inject(method = "flap", at = @At("HEAD"))
    private void polymerpatch$serverFlap(Direction side, boolean inward, CallbackInfo ci) {
        var flaps = ((BeltTunnelBlockEntity) (Object) this).flaps;
        if (flaps.containsKey(side)) flaps.get(side).setValue(inward ? -1 : 1);
    }
}
