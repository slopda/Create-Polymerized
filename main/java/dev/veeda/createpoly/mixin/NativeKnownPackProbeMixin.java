package dev.veeda.createpoly.mixin;

import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeVersionGate;
import java.util.List;
import net.minecraft.server.network.config.SynchronizeRegistriesTask;
import net.minecraft.server.packs.repository.KnownPack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(SynchronizeRegistriesTask.class)
public abstract class NativeKnownPackProbeMixin {
    @ModifyArg(
            method = "start",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/network/protocol/configuration/ClientboundSelectKnownPacks;<init>(Ljava/util/List;)V"),
            index = 0)
    private List<KnownPack> polymerProbeCreateVersions(List<KnownPack> original) {
        return PolymerNativeVersionGate.offeredPacks(original);
    }
}
