package dev.veeda.createpoly.mixin;

import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeRegistryIds;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MappedRegistry.class)
public abstract class NativeRegistryIdsMixin<T> {
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

    @Inject(method = "get(I)Ljava/util/Optional;", at = @At("HEAD"), cancellable = true)
    @SuppressWarnings("unchecked")
    private void nativeDecodeHolder(int id, CallbackInfoReturnable<Optional<Holder.Reference<T>>> cir) {
        if (PolymerNativeRegistryIds.translates(this)) {
            T value = (T) PolymerNativeRegistryIds.fromClient(this, id);
            cir.setReturnValue(
                    value == null
                            ? Optional.empty()
                            : Optional.of((Holder.Reference<T>) ((Registry<T>) (Object) this).wrapAsHolder(value)));
        }
    }
}
