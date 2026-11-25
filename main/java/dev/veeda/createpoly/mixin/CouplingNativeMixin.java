package dev.veeda.createpoly.mixin;

import com.zurrtum.create.infrastructure.polymer.contraption.PolymerMinecartInteractions;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Player.class, priority = 1100)
public abstract class CouplingNativeMixin {
    @Inject(
            method =
                    "interactOn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD"),
            cancellable = true)
    private void couple(
            Entity entity, InteractionHand hand, Vec3 location, CallbackInfoReturnable<InteractionResult> result) {
        if (NativePresentationPolicy.nativeReceiver(this)) return;
        if (PolymerMinecartInteractions.couple((Player) (Object) this, hand, entity))
            result.setReturnValue(InteractionResult.SUCCESS);
    }
}
