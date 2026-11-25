package dev.veeda.createpoly.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.zurrtum.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlock;
import com.zurrtum.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerSequencerDialogs;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = SequencedGearshiftBlock.class, remap = false)
abstract class SequencedGearshiftHookMixin {
    @ModifyExpressionValue(
            method = "useItemOn",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;isClientSide()Z", ordinal = 0))
    private boolean polymerpatch$openSequencer(
            boolean client,
            @Local(argsOnly = true) Level level,
            @Local(argsOnly = true) BlockPos pos,
            @Local(argsOnly = true) Player player) {
        if (!client
                && player instanceof ServerPlayer owner
                && !PolymerNativeClients.isNative(owner)
                && level.getBlockEntity(pos) instanceof SequencedGearshiftBlockEntity entity)
            PolymerSequencerDialogs.open(owner, entity);
        return client;
    }
}
