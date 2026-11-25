package dev.veeda.createpoly.mixin;

import com.zurrtum.create.compat.CompatMod;
import dev.veeda.createpoly.PolymerPatchCreate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CompatMod.class, remap = false)
public abstract class CompatRegistrationMixin {
    @Inject(method = "register", at = @At("TAIL"))
    private static void registerAdapter(CallbackInfo ci) {
        PolymerPatchCreate.register();
    }
}
