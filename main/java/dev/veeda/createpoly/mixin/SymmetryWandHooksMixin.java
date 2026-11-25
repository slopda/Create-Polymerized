package dev.veeda.createpoly.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.zurrtum.create.content.equipment.symmetryWand.SymmetryWandItem;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerToolDialogs;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = SymmetryWandItem.class, remap = false)
abstract class SymmetryWandHooksMixin {
    @ModifyExpressionValue(
            method = "useOn",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/world/entity/player/Player;isShiftKeyDown()Z",
                            ordinal = 0))
    private boolean polymerpatch$openFromUseOn(
            boolean sneaking, @Local(argsOnly = true) UseOnContext context, @Local Player player) {
        polymerpatch$open(sneaking, player, context.getHand());
        return sneaking;
    }

    @ModifyExpressionValue(
            method = "use",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/world/entity/player/Player;isShiftKeyDown()Z",
                            ordinal = 0))
    private boolean polymerpatch$openFromUse(
            boolean sneaking, @Local(argsOnly = true) Player player, @Local(argsOnly = true) InteractionHand hand) {
        polymerpatch$open(sneaking, player, hand);
        return sneaking;
    }

    private void polymerpatch$open(boolean sneaking, Player player, InteractionHand hand) {
        if (!sneaking || !(player instanceof ServerPlayer owner) || PolymerNativeClients.isNative(owner)) return;
        owner.getCooldowns().addCooldown(owner.getItemInHand(hand), 5);
        PolymerToolDialogs.openSymmetry(owner, hand);
    }
}
