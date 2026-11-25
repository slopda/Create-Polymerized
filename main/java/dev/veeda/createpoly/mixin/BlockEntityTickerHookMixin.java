package dev.veeda.createpoly.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.zurrtum.create.foundation.block.IBE;
import com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = IBE.class, remap = false)
interface BlockEntityTickerHookMixin {
    @ModifyReturnValue(method = "getTicker", at = @At("RETURN"))
    private BlockEntityTicker<?> polymerpatch$partsTicker(
            BlockEntityTicker<?> ticker,
            @Local(argsOnly = true) Level level,
            @Local(argsOnly = true) BlockState state) {
        if (ticker != null || level.isClientSide() || !PolymerBlockParts.handles(state)) return ticker;
        return (tickLevel, pos, tickState, entity) -> PolymerBlockParts.tick(entity);
    }
}
