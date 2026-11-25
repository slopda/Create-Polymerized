package dev.veeda.createpoly.mixin;

import com.zurrtum.create.infrastructure.polymer.effects.PolymerBlockParticles;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientboundLevelParticlesPacket.class)
public abstract class CreateBlockParticleScopeMixin {
    @Shadow
    @Final
    @Mutable
    public static StreamCodec<RegistryFriendlyByteBuf, ClientboundLevelParticlesPacket> STREAM_CODEC;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void polymerScopeCreateParticles(CallbackInfo ci) {
        StreamCodec<RegistryFriendlyByteBuf, ClientboundLevelParticlesPacket> original = STREAM_CODEC;
        STREAM_CODEC = StreamCodec.of(
                (buffer, packet) -> {
                    PolymerBlockParticles.enterCreateParticles(packet.particle() instanceof BlockParticleOption block
                            && PolymerBlockParticles.isProxy(block.getState()));
                    try {
                        original.encode(buffer, packet);
                    } finally {
                        PolymerBlockParticles.exitCreateParticles();
                    }
                },
                original::decode);
    }
}
