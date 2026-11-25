package dev.veeda.createpoly.mixin;

import com.zurrtum.create.content.trains.track.TrackBlockEntity;
import com.zurrtum.create.infrastructure.polymer.train.PolymerTrackVisuals;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TrackBlockEntity.class, remap = false)
abstract class TrackVisualsHooksMixin {
    @Inject(method = "tick", at = @At("RETURN"))
    private void polymerpatch$trackTick(CallbackInfo ci) {
        PolymerTrackVisuals.tick((TrackBlockEntity) (Object) this);
    }

    @Inject(method = "invalidate", at = @At("RETURN"))
    private void polymerpatch$trackRemove(CallbackInfo ci) {
        TrackBlockEntity track = (TrackBlockEntity) (Object) this;
        if (!track.getLevel().isClientSide()) PolymerTrackVisuals.remove(track);
    }
}
