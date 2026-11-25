package dev.veeda.createpoly.mixin;

import com.zurrtum.create.content.contraptions.AbstractContraptionEntity;
import com.zurrtum.create.infrastructure.polymer.contraption.PolymerContraptionVisuals;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AbstractContraptionEntity.class, remap = false)
abstract class ContraptionVisualsHooksMixin {
    @Inject(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/zurrtum/create/content/contraptions/AbstractContraptionEntity;getPassengers()Ljava/util/List;",
                            ordinal = 0))
    private void polymerpatch$contraptionTick(CallbackInfo ci) {
        PolymerContraptionVisuals.tick((AbstractContraptionEntity) (Object) this);
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void polymerpatch$contraptionRemove(Entity.RemovalReason reason, CallbackInfo ci) {
        PolymerContraptionVisuals.remove((AbstractContraptionEntity) (Object) this);
    }
}
