package dev.veeda.createpoly.mixin;

import com.zurrtum.create.infrastructure.polymer.PolymerRegistrySync;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MappedRegistry.class)
public abstract class MappedRegistryServerEntriesMixin<T> {
    @Inject(method = "freeze", at = @At("HEAD"))
    private void markCreateEntriesBeforeFreeze(CallbackInfoReturnable<Registry<T>> cir) {
        PolymerRegistrySync.markRegistryEntriesAsServerOnly((Registry<?>) (Object) this);
    }
}
