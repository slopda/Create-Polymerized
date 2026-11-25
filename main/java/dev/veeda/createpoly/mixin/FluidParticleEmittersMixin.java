package dev.veeda.createpoly.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.zurrtum.create.catnip.data.IntAttached;
import com.zurrtum.create.content.fluids.spout.SpoutBlockEntity;
import com.zurrtum.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour.ProcessingResult;
import com.zurrtum.create.content.kinetics.mixer.MechanicalMixerBlockEntity;
import com.zurrtum.create.content.processing.basin.BasinBlockEntity;
import com.zurrtum.create.infrastructure.fluids.FluidStack;
import com.zurrtum.create.infrastructure.polymer.effects.PolymerFluidParticles;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public final class FluidParticleEmittersMixin {
    @Mixin(SpoutBlockEntity.class)
    public abstract static class Spout {
        @Unique
        private FluidStack polymerpatch$beforeFluid = FluidStack.EMPTY;

        @Unique
        private void polymerpatch$capture() {
            var spout = (SpoutBlockEntity) (Object) this;
            if (spout.tank != null)
                polymerpatch$beforeFluid =
                        spout.tank.getPrimaryHandler().getFluid().copy();
        }

        @Inject(method = "tick", at = @At("HEAD"))
        private void captureTick(CallbackInfo ci) {
            polymerpatch$capture();
        }

        @Inject(method = "whenItemHeld", at = @At("HEAD"))
        private void captureFilling(CallbackInfoReturnable<ProcessingResult> cir) {
            polymerpatch$capture();
        }

        @WrapOperation(
                method = {"tick", "whenItemHeld"},
                at =
                        @At(
                                value = "FIELD",
                                target = "Lcom/zurrtum/create/content/fluids/spout/SpoutBlockEntity;sendSplash:Z",
                                opcode = Opcodes.PUTFIELD))
        private void splash(SpoutBlockEntity spout, boolean value, Operation<Void> original) {
            original.call(spout, value);
            if (value) PolymerFluidParticles.splash(spout, polymerpatch$beforeFluid);
        }

        @Inject(method = "tick", at = @At("RETURN"))
        private void stream(CallbackInfo ci) {
            PolymerFluidParticles.spout((SpoutBlockEntity) (Object) this);
            polymerpatch$beforeFluid = FluidStack.EMPTY;
        }

        @Inject(method = "whenItemHeld", at = @At("RETURN"))
        private void clear(CallbackInfoReturnable<ProcessingResult> cir) {
            polymerpatch$beforeFluid = FluidStack.EMPTY;
        }
    }

    @Mixin(MechanicalMixerBlockEntity.class)
    public abstract static class Mixer {
        @Inject(method = "tick", at = @At("HEAD"))
        private void particles(CallbackInfo ci) {
            PolymerFluidParticles.mixer((MechanicalMixerBlockEntity) (Object) this);
        }
    }

    @Mixin(BasinBlockEntity.class)
    public abstract static class Basin {
        @Unique
        private final List<IntAttached<FluidStack>> polymerpatch$outputs = new ArrayList<>();

        @WrapOperation(
                method = "tryClearingSpoutputOverflow",
                at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z"))
        private boolean captureOutput(List<Object> list, Object value, Operation<Boolean> original) {
            boolean added = original.call(list, value);
            if (added
                    && value instanceof IntAttached<?> attached
                    && attached.getValue() instanceof FluidStack fluid
                    && !fluid.isEmpty()) {
                if (polymerpatch$outputs.size() == 3) polymerpatch$outputs.removeFirst();
                polymerpatch$outputs.add(IntAttached.with(BasinBlockEntity.OUTPUT_ANIMATION_TIME, fluid.copy()));
            }
            return added;
        }

        @Inject(method = "tick", at = @At("RETURN"))
        private void particles(CallbackInfo ci) {
            var basin = (BasinBlockEntity) (Object) this;
            PolymerFluidParticles.basin(basin);
            for (var output : polymerpatch$outputs) {
                PolymerFluidParticles.basinOutput(basin, output.getValue());
                output.decrement();
            }
            polymerpatch$outputs.removeIf(IntAttached::isOrBelowZero);
        }
    }
}
