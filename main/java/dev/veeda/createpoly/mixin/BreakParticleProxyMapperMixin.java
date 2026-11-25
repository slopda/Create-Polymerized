package dev.veeda.createpoly.mixin;

import com.zurrtum.create.infrastructure.polymer.effects.PolymerBlockParticles;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "eu.pb4.polymer.blocks.impl.BlockExtBlockMapper", remap = false)
public abstract class BreakParticleProxyMapperMixin {
    @Inject(method = "toClientSideState", at = @At("HEAD"), cancellable = true)
    private void polymerKeepParticleProxy(
            BlockState state, PacketContext context, CallbackInfoReturnable<BlockState> cir) {
        if (PolymerBlockParticles.encodingCreateParticles() && PolymerBlockParticles.isProxy(state))
            cir.setReturnValue(state);
    }
}
