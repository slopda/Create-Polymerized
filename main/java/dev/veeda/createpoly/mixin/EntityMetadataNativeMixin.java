package dev.veeda.createpoly.mixin;

import dev.veeda.createpoly.NativeMetadataSnapshots;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMetadataNativeMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void publishNativeMetadataBoundary(CallbackInfo ci) {
        NativeMetadataSnapshots.capture((Entity) (Object) this);
    }

    @Inject(method = "setId", at = @At("HEAD"))
    private void forgetOldMetadataBoundary(int id, CallbackInfo ci) {
        NativeMetadataSnapshots.remove((Entity) (Object) this);
    }

    @Inject(method = "setId", at = @At("RETURN"))
    private void publishChangedMetadataBoundary(int id, CallbackInfo ci) {
        NativeMetadataSnapshots.capture((Entity) (Object) this);
    }
}
