package dev.veeda.createpoly.mixin;

import com.zurrtum.create.content.equipment.bell.HauntedBellPulser;
import com.zurrtum.create.infrastructure.polymer.effects.PolymerSoulPulse;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HauntedBellPulser.class)
public abstract class SoulPulseNativeMixin {
    @Inject(method = "sendPulse", at = @At("HEAD"))
    private static void polymerSoulPulse(
            ServerLevel world, BlockPos pos, int distance, boolean canOverlap, CallbackInfo ci) {
        PolymerSoulPulse.add(world, pos.immutable(), distance, canOverlap);
    }
}
