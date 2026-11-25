package dev.veeda.createpoly.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientboundLevelParticlesPacket.class)
public abstract class NativeParticlePacketMixin {
    @Unique
    private ParticleOptions nativeOriginalParticle;

    @Inject(
            method =
                    "<init>(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDFFFFFFILnet/minecraft/network/protocol/game/ClientboundLevelParticlesPacket$RandomizationType;)V",
            at = @At("RETURN"))
    private void preserveNativeParticle(
            ParticleOptions particle,
            boolean override,
            boolean always,
            double x,
            double y,
            double z,
            float xd,
            float yd,
            float zd,
            float xs,
            float ys,
            float zs,
            int count,
            ClientboundLevelParticlesPacket.RandomizationType randomization,
            CallbackInfo ci) {
        nativeOriginalParticle = particle;
    }

    @WrapMethod(method = "particle")
    private ParticleOptions nativeParticle(Operation<ParticleOptions> original) {
        ParticleOptions projected = original.call();
        return nativeOriginalParticle != null && PolymerNativeClients.isNative(PacketContext.get())
                ? nativeOriginalParticle
                : projected;
    }
}
