package dev.veeda.createpoly.mixin;

import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeRegistryIds;
import net.minecraft.core.IdMapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(IdMapper.class)
public abstract class NativeStateIdsMixin<T> {
    @Inject(method = "getId", at = @At("RETURN"), cancellable = true)
    private void nativeEncodeId(T value, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(PolymerNativeRegistryIds.toClient(this, value, cir.getReturnValueI()));
    }

    @Inject(method = "byId", at = @At("HEAD"), cancellable = true)
    @SuppressWarnings("unchecked")
    private void nativeDecodeId(int id, CallbackInfoReturnable<T> cir) {
        if (PolymerNativeRegistryIds.translates(this))
            cir.setReturnValue((T) PolymerNativeRegistryIds.fromClient(this, id));
    }
}
