package dev.veeda.createpoly.mixin;

import com.zurrtum.create.Create;
import com.zurrtum.create.infrastructure.particle.CubeParticleData;
import com.zurrtum.create.infrastructure.particle.RotationIndicatorParticleData;
import com.zurrtum.create.infrastructure.polymer.effects.PolymerBlockParticles;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientboundLevelParticlesPacket.class)
public abstract class ClientboundLevelParticlesPacketMixin {
    private static final AtomicBoolean REPORTED_FALLBACK = new AtomicBoolean();

    @Shadow
    @Final
    @Mutable
    private ParticleOptions particle;

    @Inject(
            method =
                    "<init>(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDFFFFFFILnet/minecraft/network/protocol/game/ClientboundLevelParticlesPacket$RandomizationType;)V",
            at = @At("TAIL"))
    private <T extends ParticleOptions> void useVanillaParticleForCreateEffects(
            T particle,
            boolean overrideLimiter,
            boolean alwaysShow,
            double x,
            double y,
            double z,
            float xDist,
            float yDist,
            float zDist,
            float xMaxSpeed,
            float yMaxSpeed,
            float zMaxSpeed,
            int count,
            ClientboundLevelParticlesPacket.RandomizationType randomizationType,
            CallbackInfo ci) {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.SERVER) {
            return;
        }

        ParticleOptions terrain = PolymerBlockParticles.project(particle);
        if (terrain != particle) {
            this.particle = terrain;
            return;
        }

        Identifier id = BuiltInRegistries.PARTICLE_TYPE.getKey(particle.getType());
        if (id == null || !id.getNamespace().equals(Create.MOD_ID)) {
            return;
        }

        this.particle = switch (id.getPath()) {
            case "rotation_indicator" -> {
                RotationIndicatorParticleData data = (RotationIndicatorParticleData) particle;
                int color = data.color(), light = 0;
                for (int shift : new int[] {0, 8, 16}) light |= ((((color >> shift) & 255) + 255) / 2) << shift;
                yield new DustColorTransitionOptions(light, color, .75f);
            }
            case "cube" -> {
                CubeParticleData data = (CubeParticleData) particle;
                int color = (Math.clamp(Math.round(data.red() * 255), 0, 255) << 16)
                        | (Math.clamp(Math.round(data.green() * 255), 0, 255) << 8)
                        | Math.clamp(Math.round(data.blue() * 255), 0, 255);
                yield new DustParticleOptions(color, Math.clamp(data.scale(), .01f, 4f));
            }
            case "air", "air_flow" -> ParticleTypes.WHITE_SMOKE;
            case "steam_jet" -> ParticleTypes.CLOUD;
            case "fluid_particle", "basin_fluid" -> ParticleTypes.SPLASH;
            case "wifi" -> ParticleTypes.HAPPY_VILLAGER;
            case "soul", "soul_base", "soul_perimeter", "soul_expanding_perimeter" -> ParticleTypes.SOUL_FIRE_FLAME;
            default -> ParticleTypes.POOF;
        };

        if (REPORTED_FALLBACK.compareAndSet(false, true)) {
            Create.LOGGER.info(
                    "Mapped Create particle {} to vanilla particle {} for vanilla-client packets",
                    id,
                    BuiltInRegistries.PARTICLE_TYPE.getKey(this.particle.getType()));
        }
    }
}
