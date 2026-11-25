package dev.veeda.createpoly.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.zurrtum.create.content.contraptions.elevator.ElevatorContactBlock;
import com.zurrtum.create.content.contraptions.elevator.ElevatorContactBlockEntity;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerElevatorDialogs;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ElevatorContactBlock.class, remap = false)
abstract class ElevatorContactHookMixin {
    @ModifyExpressionValue(
            method = "useItemOn",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;isClientSide()Z", ordinal = 0))
    private boolean polymerpatch$openElevatorContact(
            boolean client,
            @Local(argsOnly = true) Level level,
            @Local(argsOnly = true) BlockPos pos,
            @Local(argsOnly = true) Player player) {
        if (!client
                && player instanceof ServerPlayer owner
                && !PolymerNativeClients.isNative(owner)
                && level.getBlockEntity(pos) instanceof ElevatorContactBlockEntity entity)
            PolymerElevatorDialogs.open(owner, entity);
        return client;
    }
}
