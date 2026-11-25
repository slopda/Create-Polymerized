package dev.veeda.createpoly.mixin;

import com.zurrtum.create.Create;
import com.zurrtum.create.infrastructure.polymer.effects.PolymerBlockParticles;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Block.class)
public abstract class BreakParticlesCreativeMixin {
    @Inject(method = "spawnDestroyByEntityParticles", at = @At("HEAD"))
    private void polymerCreativeBreakParticles(
            Level level, Entity entity, BlockPos pos, BlockState state, CallbackInfo ci) {
        if (!(level instanceof ServerLevel serverLevel)
                || !(entity instanceof ServerPlayer player)
                || !player.getAbilities().instabuild
                || PolymerNativeClients.isNative(player)
                || !BuiltInRegistries.BLOCK
                        .getKey(state.getBlock())
                        .getNamespace()
                        .equals(Create.MOD_ID)) return;
        player.connection.send(new ClientboundLevelEventPacket(
                LevelEvent.PARTICLES_DESTROY_BLOCK,
                pos,
                PolymerBlockParticles.breakEvent(serverLevel, pos, Block.getId(state)),
                false));
    }
}
