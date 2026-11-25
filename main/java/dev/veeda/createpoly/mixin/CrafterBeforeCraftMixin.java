package dev.veeda.createpoly.mixin;

import com.zurrtum.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import com.zurrtum.create.infrastructure.polymer.block.PolymerCrafterParts;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MechanicalCrafterBlockEntity.class)
public abstract class CrafterBeforeCraftMixin {
    @Inject(method = "write", at = @At("HEAD"))
    private void polymerCaptureBeforeCraft(ValueOutput view, boolean clientPacket, CallbackInfo ci) {
        MechanicalCrafterBlockEntity self = (MechanicalCrafterBlockEntity) (Object) this;
        if (self.groupedItemsBeforeCraft != null)
            PolymerCrafterParts.captureBeforeCraft(self, self.groupedItemsBeforeCraft);
    }
}
