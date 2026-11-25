package dev.veeda.createpoly.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.zurrtum.create.content.kinetics.fan.AirCurrent;
import com.zurrtum.create.infrastructure.polymer.effects.PolymerFanParticles;
import net.minecraft.server.level.ServerPlayer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AirCurrent.class, remap = false)
abstract class AirCurrentHooksMixin {
    @Inject(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/zurrtum/create/content/kinetics/fan/AirCurrent;tickAffectedEntities(Lnet/minecraft/world/level/Level;)V"))
    private void polymerpatch$fanParticles(CallbackInfo ci) {
        PolymerFanParticles.tick((AirCurrent) (Object) this);
    }

    @Inject(
            method = "tickAffectedEntities",
            at =
                    @At(
                            value = "FIELD",
                            opcode = Opcodes.PUTFIELD,
                            target =
                                    "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;aboveGroundTickCount:I",
                            shift = At.Shift.AFTER))
    private void polymerpatch$syncPushedPlayer(CallbackInfo ci, @Local ServerPlayer player) {
        player.hurtMarked = true;
    }
}
