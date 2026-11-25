package dev.veeda.createpoly.devmixin;

import com.zurrtum.create.Create;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
abstract class LevelEventTraceMixin {
    @Inject(
            method = "levelEvent(Lnet/minecraft/world/entity/Entity;ILnet/minecraft/core/BlockPos;I)V",
            at = @At("HEAD"),
            require = 0)
    private void createpolyDev$traceLevelEvent(Entity excluded, int type, BlockPos pos, int data, CallbackInfo ci) {
        if (type != LevelEvent.PARTICLES_DESTROY_BLOCK) return;
        Create.LOGGER.info(
                "[break-trace] levelEvent excluding {} at {} data {} = {}",
                excluded == null ? "nobody" : excluded.getName().getString(),
                pos,
                data,
                Block.stateById(data));
    }
}
