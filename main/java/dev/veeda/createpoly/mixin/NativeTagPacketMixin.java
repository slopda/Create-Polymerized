package dev.veeda.createpoly.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeRegistryIds;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.Registry;
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagNetworkSerialization;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientboundUpdateTagsPacket.class)
public abstract class NativeTagPacketMixin {
    @Unique
    private Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> nativeOriginalTags;

    @Inject(method = "<init>(Ljava/util/Map;)V", at = @At("RETURN"))
    private void retainOriginalTags(
            Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> tags, CallbackInfo ci) {
        nativeOriginalTags = tags;
    }

    @WrapMethod(method = "tags")
    private Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> nativeTags(
            Operation<Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload>> original) {
        if (nativeOriginalTags != null && PolymerNativeClients.isNative(PacketContext.get())) {
            return PolymerNativeRegistryIds.tags(nativeOriginalTags);
        }
        return original.call();
    }
}
