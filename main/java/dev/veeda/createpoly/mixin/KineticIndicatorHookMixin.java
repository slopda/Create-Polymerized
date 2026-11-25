package dev.veeda.createpoly.mixin;

import com.zurrtum.create.content.kinetics.base.KineticBlockEntity;
import com.zurrtum.create.content.kinetics.base.KineticEffectHandler;
import com.zurrtum.create.content.kinetics.crank.HandCrankBlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = KineticEffectHandler.class, remap = false)
abstract class KineticIndicatorHookMixin {
    @Shadow
    @Final
    KineticBlockEntity kte;

    @Inject(method = "spawnRotationIndicators", at = @At("HEAD"), cancellable = true)
    private void polymerpatch$noCrankIndicators(CallbackInfo ci) {
        if (kte instanceof HandCrankBlockEntity) ci.cancel();
    }
}
