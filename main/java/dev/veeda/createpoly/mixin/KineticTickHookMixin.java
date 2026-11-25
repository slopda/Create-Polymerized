package dev.veeda.createpoly.mixin;

import com.zurrtum.create.content.kinetics.base.KineticBlockEntity;
import com.zurrtum.create.infrastructure.polymer.block.PolymerKineticVisuals;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = KineticBlockEntity.class, remap = false)
abstract class KineticTickHookMixin {
    @Inject(
            method = "tick",
            at =
                    @At(
                            value = "FIELD",
                            target =
                                    "Lcom/zurrtum/create/content/kinetics/base/KineticBlockEntity;validationCountdown:I",
                            opcode = Opcodes.GETFIELD,
                            ordinal = 0))
    private void polymerpatch$kineticTick(CallbackInfo ci) {
        PolymerKineticVisuals.tick((KineticBlockEntity) (Object) this);
    }
}
