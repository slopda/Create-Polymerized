package dev.veeda.createpoly.devmixin;

import com.zurrtum.create.Create;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
abstract class PlayerWillDestroyTraceMixin {
    @Inject(method = "playerWillDestroy", at = @At("HEAD"), require = 0)
    private void createpolyDev$traceWillDestroy(
            Level level, BlockPos pos, BlockState state, Player player, CallbackInfoReturnable<BlockState> cir) {
        if (BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals(Create.MOD_ID))
            Create.LOGGER.info("[break-trace] playerWillDestroy {} at {} client {}", state, pos, level.isClientSide());
    }

    @Inject(method = "spawnDestroyByEntityParticles", at = @At("HEAD"), require = 0)
    private void createpolyDev$traceSpawn(
            Level level, net.minecraft.world.entity.Entity entity, BlockPos pos, BlockState state, CallbackInfo ci) {
        if (BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals(Create.MOD_ID))
            Create.LOGGER.info(
                    "[break-trace] spawnDestroyByEntityParticles {} at {} level {}",
                    state,
                    pos,
                    level.getClass().getSimpleName());
    }
}
