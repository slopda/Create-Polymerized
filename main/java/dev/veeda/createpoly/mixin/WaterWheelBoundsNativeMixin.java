package dev.veeda.createpoly.mixin;

import com.zurrtum.create.content.kinetics.waterwheel.LargeWaterWheelBlock;
import com.zurrtum.create.content.kinetics.waterwheel.LargeWaterWheelBlockItem;
import com.zurrtum.create.infrastructure.polymer.block.PolymerWaterWheelPlacement;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LargeWaterWheelBlockItem.class)
public abstract class WaterWheelBoundsNativeMixin {
    @Inject(
            method =
                    "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;",
            at = @At("RETURN"))
    private void polymerWaterWheelBounds(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (cir.getReturnValue() != InteractionResult.FAIL
                || context.getLevel().isClientSide()
                || !(context.getPlayer() instanceof ServerPlayer player)) return;
        var block = (LargeWaterWheelBlock) ((LargeWaterWheelBlockItem) (Object) this).getBlock();
        PolymerWaterWheelPlacement.showBounds(player, block.getAxisForPlacement(context), context.getClickedPos());
    }
}
