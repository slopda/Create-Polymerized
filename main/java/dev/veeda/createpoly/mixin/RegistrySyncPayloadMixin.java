package dev.veeda.createpoly.mixin;

import com.zurrtum.create.infrastructure.polymer.PolymerRegistrySyncFilter;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.fabricmc.fabric.impl.registry.sync.packet.RegistrySyncPayload")
public class RegistrySyncPayloadMixin {
    @Shadow
    @Final
    private Map<Identifier, Object2IntMap<Identifier>> registryMap;

    @Inject(method = "write", at = @At("HEAD"))
    private void omitCreateEntriesBeforeEncoding(FriendlyByteBuf buffer, CallbackInfo ci) {
        PolymerRegistrySyncFilter.removeCreateEntries(registryMap, "outgoing Fabric registry sync payload");
    }
}
