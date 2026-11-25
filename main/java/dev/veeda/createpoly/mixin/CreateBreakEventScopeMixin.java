package dev.veeda.createpoly.mixin;

import com.zurrtum.create.infrastructure.polymer.effects.PolymerBlockParticles;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientboundLevelEventPacket.class)
public abstract class CreateBreakEventScopeMixin {
    @Shadow
    @Final
    private int type;

    @Shadow
    @Final
    private int data;

    @Inject(method = "write", at = @At("HEAD"))
    private void polymerCreateBreakStart(FriendlyByteBuf buffer, CallbackInfo ci) {
        if (type != LevelEvent.PARTICLES_DESTROY_BLOCK) return;
        var state = Block.stateById(data);
        PolymerBlockParticles.enterCreateParticles(
                BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals("create")
                        || PolymerBlockParticles.isProxy(state));
    }

    @Inject(method = "write", at = @At("RETURN"))
    private void polymerCreateBreakEnd(FriendlyByteBuf buffer, CallbackInfo ci) {
        PolymerBlockParticles.exitCreateParticles();
    }
}
