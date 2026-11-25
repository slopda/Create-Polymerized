package dev.veeda.createpoly.devmixin;

import com.zurrtum.create.infrastructure.polymer.PolymerPackWrites;
import eu.pb4.polymer.resourcepack.api.PackResource;
import eu.pb4.polymer.resourcepack.impl.generation.DefaultRPBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = DefaultRPBuilder.class, remap = false)
abstract class PackWriteRecorderMixin {
    @Inject(method = "addData(Ljava/lang/String;Leu/pb4/polymer/resourcepack/api/PackResource;)Z", at = @At("HEAD"))
    private void polymerpatchDev$record(String path, PackResource resource, CallbackInfoReturnable<Boolean> ci) {
        PolymerPackWrites.record(path, resource.readAllBytes());
    }
}
