package dev.veeda.createpoly.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import java.util.function.Consumer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ServerEntity.class)
public abstract class NativeEntityPairingMixin {
    @WrapMethod(method = "sendPairingData")
    private void nativePairing(ServerPlayer player, Consumer<Packet<?>> consumer, Operation<Void> original) {
        ServerPlayer previous = NativePresentationPolicy.enterPairing(player);
        try {
            original.call(player, consumer);
        } finally {
            NativePresentationPolicy.exitPairing(previous);
        }
    }
}
