package dev.veeda.createpoly.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.zurrtum.create.content.redstone.displayLink.DisplayLinkBlock;
import com.zurrtum.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerMachineDialogs;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = DisplayLinkBlock.class, remap = false)
abstract class DisplayLinkHookMixin {
    @ModifyExpressionValue(
            method = "useWithoutItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;isClientSide()Z", ordinal = 0))
    private boolean polymerpatch$openDisplayLink(
            boolean client,
            @Local(argsOnly = true) Level level,
            @Local(argsOnly = true) BlockPos pos,
            @Local(argsOnly = true) Player player) {
        if (!client
                && player instanceof ServerPlayer owner
                && !PolymerNativeClients.isNative(owner)
                && level instanceof ServerLevel server
                && server.getBlockEntity(pos) instanceof DisplayLinkBlockEntity link)
            PolymerMachineDialogs.openDisplayLinkSettings(owner, server, pos, link);
        return client;
    }
}
