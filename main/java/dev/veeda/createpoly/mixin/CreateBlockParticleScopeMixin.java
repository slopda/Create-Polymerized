package dev.veeda.createpoly.mixin;

import com.zurrtum.create.infrastructure.polymer.effects.PolymerBlockParticles;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientboundLevelParticlesPacket.class)
public abstract class CreateBlockParticleScopeMixin {
    @Shadow
    @Final
    private ParticleOptions particle;

    @Inject(method = "write", at = @At("HEAD"))
    private void polymerCreateParticleStart(RegistryFriendlyByteBuf buffer, CallbackInfo ci) {
        PolymerBlockParticles.enterCreateParticles(
                particle instanceof BlockParticleOption block && PolymerBlockParticles.isProxy(block.getState()));
    }

    @Inject(method = "write", at = @At("RETURN"))
    private void polymerCreateParticleEnd(RegistryFriendlyByteBuf buffer, CallbackInfo ci) {
        PolymerBlockParticles.exitCreateParticles();
    }
}
