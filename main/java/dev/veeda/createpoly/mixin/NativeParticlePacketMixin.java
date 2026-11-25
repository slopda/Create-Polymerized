package dev.veeda.createpoly.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
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

    @Inject(method = "<init>(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDFFFFI)V", at = @At("RETURN"))
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
            float speed,
            int count,
            CallbackInfo ci) {
        nativeOriginalParticle = particle;
    }

    @ModifyExpressionValue(
            method = "write",
            at =
                    @At(
                            value = "FIELD",
                            target =
                                    "Lnet/minecraft/network/protocol/game/ClientboundLevelParticlesPacket;particle:Lnet/minecraft/core/particles/ParticleOptions;"))
    private ParticleOptions nativeParticle(ParticleOptions projected) {
        return nativeOriginalParticle != null && PolymerNativeClients.isNative(PacketContext.get())
                ? nativeOriginalParticle
                : projected;
    }
}
