package dev.veeda.createpoly.mixin;

import com.zurrtum.create.infrastructure.polymer.block.PolymerPlacementAnimations;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class PlacementNativeMixin {

    @Inject(method = "place", at = @At("RETURN"), cancellable = true)
    private void polymerPlacementSwing(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (NativePresentationPolicy.nativeReceiver(context)) return;
        cir.setReturnValue(PolymerPlacementAnimations.result(
                context.getPlayer(), (BlockItem) (Object) this, cir.getReturnValue()));
    }
}
