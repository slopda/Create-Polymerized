package dev.veeda.createpoly.mixin;

import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(targets = "net.fabricmc.fabric.impl.base.event.ArrayBackedEvent", remap = false)
public abstract class NativeAdapterCallbacksMixin {
    @ModifyVariable(
            method = "register(Lnet/minecraft/resources/Identifier;Ljava/lang/Object;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0)
    private Object nativeCallback(Object callback) {
        return NativePresentationPolicy.playerCallback(callback);
    }
}
