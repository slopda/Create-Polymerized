package dev.veeda.createpoly.mixin;

import com.zurrtum.create.Create;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeVersionGate;
import net.minecraft.network.protocol.configuration.ServerboundSelectKnownPacks;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerConfigurationPacketListenerImpl.class)
public abstract class NativeClientDetectMixin {
    @Inject(
            method = "handleSelectKnownPacks",
            at =
                    @At(
                            value = "INVOKE",
                            shift = At.Shift.AFTER,
                            target =
                                    "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V"),
            cancellable = true)
    private void polymerDetectNativeCreate(ServerboundSelectKnownPacks packet, CallbackInfo ci) {
        var listener = (ServerConfigurationPacketListenerImpl) (Object) this;
        var mismatch = PolymerNativeVersionGate.mismatch(packet.knownPacks());
        if (mismatch.isPresent()) {
            var profile = listener.getOwner();
            if (profile != null) PolymerNativeClients.forget(profile.id());
            String required = PolymerNativeVersionGate.requiredVersion();
            Create.LOGGER.info(
                    "Rejected mismatched Create client {}: requires {}, reports {}",
                    profile == null ? "unknown" : profile.name(),
                    required,
                    mismatch.get());
            listener.disconnect(PolymerNativeVersionGate.warning(required, mismatch.get()));
            ci.cancel();
            return;
        }
        PolymerNativeClients.onKnownPacks(listener.getOwner(), packet.knownPacks());
    }
}
