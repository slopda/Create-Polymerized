package dev.veeda.createpoly.mixin;

import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.Registry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PolymerSyncedObject.class, remap = false)
public interface NativeSyncedObjectsMixin {
    @Inject(method = "getSyncedObject", at = @At("HEAD"), cancellable = true)
    private static <T> void nativeObject(
            Registry<T> registry, T value, CallbackInfoReturnable<PolymerSyncedObject<T>> cir) {
        if (registry == null) return;
        var key = registry.getKey(value);
        if (key != null && key.getNamespace().equals("create") && PolymerNativeClients.isNative(PacketContext.get()))
            cir.setReturnValue(null);
    }

    @Inject(
            method =
                    "canSyncRawToClient(Lnet/minecraft/core/Registry;Ljava/lang/Object;Lnet/fabricmc/fabric/api/networking/v1/context/PacketContext;)Z",
            at = @At("HEAD"),
            cancellable = true)
    private static <T> void nativeRaw(
            Registry<T> registry, T value, PacketContext context, CallbackInfoReturnable<Boolean> cir) {
        if (registry == null) return;
        var key = registry.getKey(value);
        if (key != null && key.getNamespace().equals("create") && PolymerNativeClients.isNative(context))
            cir.setReturnValue(true);
    }
}
