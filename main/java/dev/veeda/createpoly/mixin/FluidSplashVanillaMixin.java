package dev.veeda.createpoly.mixin;

import com.zurrtum.create.content.fluids.transfer.FluidManipulationBehaviour;
import com.zurrtum.create.infrastructure.polymer.effects.PolymerVanillaEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FluidManipulationBehaviour.class)
public abstract class FluidSplashVanillaMixin {
    @Inject(method = "playEffect", at = @At("TAIL"))
    private void polymerVanillaSplash(Level world, BlockPos pos, Fluid fluid, boolean fillSound, CallbackInfo ci) {
        if (!(world instanceof ServerLevel level) || pos == null || fluid == null || fluid == Fluids.EMPTY) return;
        var state = fluid.defaultFluidState();
        if (state.isEmpty()) return;
        PolymerVanillaEffects.particles(
                level,
                new BlockParticleOption(ParticleTypes.BLOCK, state.createLegacyBlock()),
                Vec3.atCenterOf(pos),
                20,
                .25,
                .25,
                .25,
                .05);
    }
}
