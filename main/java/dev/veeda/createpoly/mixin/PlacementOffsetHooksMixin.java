package dev.veeda.createpoly.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.zurrtum.create.catnip.placement.PlacementOffset;
import com.zurrtum.create.infrastructure.polymer.block.PolymerPlacementAnimations;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = PlacementOffset.class, remap = false)
abstract class PlacementOffsetHooksMixin {
    @ModifyReturnValue(method = "placeInWorld", at = @At("RETURN"))
    private InteractionResult polymerpatch$placementResult(
            InteractionResult result, @Local(argsOnly = true) BlockItem item, @Local(argsOnly = true) Player player) {
        return NativePresentationPolicy.nativeReceiver(player)
                ? result
                : PolymerPlacementAnimations.result(player, item, result);
    }
}
