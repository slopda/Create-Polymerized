package dev.veeda.createpoly.mixin;

import com.zurrtum.create.content.contraptions.AbstractContraptionEntity;
import com.zurrtum.create.infrastructure.polymer.contraption.PolymerContraptionPassengers;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AbstractContraptionEntity.class, remap = false)
public abstract class ContraptionPassengerNativeMixin {
    @Inject(method = "removePassenger", at = @At("HEAD"))
    private void detachNativeSeat(Entity passenger, CallbackInfo ci) {
        if ((Object) this instanceof AbstractContraptionEntity contraption)
            PolymerContraptionPassengers.beforePassengerRemoved(contraption, passenger);
    }
}
