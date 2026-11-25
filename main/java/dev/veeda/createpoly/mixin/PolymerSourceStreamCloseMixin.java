package dev.veeda.createpoly.mixin;

import eu.pb4.polymer.resourcepack.impl.generation.DefaultRPBuilder;
import java.io.IOException;
import java.io.InputStream;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = DefaultRPBuilder.class, remap = false)
public abstract class PolymerSourceStreamCloseMixin {
    @Shadow
    private InputStream getSourceStream(String path) {
        throw new AssertionError();
    }

    @Inject(method = "getSourceData", at = @At("HEAD"), cancellable = true)
    private void polymerpatch$closeSourceStream(String path, CallbackInfoReturnable<byte[]> cir) {
        try (InputStream stream = getSourceStream(path)) {
            cir.setReturnValue(stream == null ? null : stream.readAllBytes());
        } catch (IOException | RuntimeException error) {
            LoggerFactory.getLogger("polymer-resource-pack")
                    .warn("Error occurred while getting data from vanilla jar!", error);
            cir.setReturnValue(null);
        }
    }
}
