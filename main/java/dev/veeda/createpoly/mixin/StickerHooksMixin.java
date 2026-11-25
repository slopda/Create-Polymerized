package dev.veeda.createpoly.mixin;

import com.zurrtum.create.catnip.animation.LerpedFloat;
import com.zurrtum.create.catnip.animation.LerpedFloat.Chaser;
import com.zurrtum.create.content.contraptions.chassis.StickerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = StickerBlockEntity.class, remap = false)
abstract class StickerHooksMixin {
    @Shadow
    public LerpedFloat piston;

    @Shadow
    public abstract boolean isBlockStateExtended();

    @Inject(method = "initialize", at = @At("RETURN"))
    private void polymerpatch$serverPistonStart(CallbackInfo ci) {
        if (!((StickerBlockEntity) (Object) this).getLevel().isClientSide())
            piston.startWithValue(isBlockStateExtended() ? 1 : 0);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void polymerpatch$serverPistonTick(CallbackInfo ci) {
        if (((StickerBlockEntity) (Object) this).getLevel().isClientSide()) return;
        piston.chase(isBlockStateExtended() ? 1 : 0, 0.4f, Chaser.LINEAR);
        piston.tickChaser();
    }
}
