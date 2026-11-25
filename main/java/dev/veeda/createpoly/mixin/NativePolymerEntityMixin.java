package dev.veeda.createpoly.mixin;

import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PolymerEntity.class, remap = false)
public interface NativePolymerEntityMixin {
    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private static void nativeEntity(Entity entity, CallbackInfoReturnable<PolymerEntity> cir) {
        if (entity != null
                && NativePresentationPolicy.useOriginal(null)
                && BuiltInRegistries.ENTITY_TYPE
                        .getKey(entity.getType())
                        .getNamespace()
                        .equals("create")) cir.setReturnValue(null);
    }
}
