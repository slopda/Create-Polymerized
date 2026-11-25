package dev.veeda.createpoly.mixin;

import com.zurrtum.create.infrastructure.polymer.PolymerRegistrySyncFilter;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagNetworkSerialization;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientboundUpdateTagsPacket.class)
public abstract class RegistryTagSyncFilterMixin {
    @Shadow
    @Final
    @Mutable
    private Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> tags;

    @Inject(method = "<init>(Ljava/util/Map;)V", at = @At("RETURN"))
    private void omitCreateRegistryTags(
            Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> tags, CallbackInfo ci) {
        this.tags = PolymerRegistrySyncFilter.removeCreateRegistryTags(tags);
    }
}
