package dev.veeda.createpoly.mixin;

import com.zurrtum.create.content.kinetics.base.KineticBlockEntity;
import com.zurrtum.create.foundation.blockEntity.SmartBlockEntity;
import com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SmartBlockEntity.class, remap = false)
abstract class SmartBlockEntityHooksMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void polymerpatch$partsTick(CallbackInfo ci) {
        SmartBlockEntity self = (SmartBlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level != null && !level.isClientSide() && !((Object) this instanceof KineticBlockEntity))
            PolymerBlockParts.tick(self);
    }

    @Inject(
            method = "setRemoved",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/zurrtum/create/foundation/blockEntity/CachedRenderBBBlockEntity;setRemoved()V",
                            shift = At.Shift.AFTER))
    private void polymerpatch$partsRemove(CallbackInfo ci) {
        PolymerBlockParts.remove((SmartBlockEntity) (Object) this);
    }
}
