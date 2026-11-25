package dev.veeda.createpoly.mixin;

import com.zurrtum.create.content.equipment.clipboard.ClipboardBlockItem;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerToolDialogs;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ClipboardBlockItem.class, remap = false)
abstract class ClipboardItemHookMixin {
    @Inject(
            method = "use",
            at =
                    @At(
                            value = "INVOKE",
                            shift = At.Shift.AFTER,
                            target =
                                    "Lnet/minecraft/world/item/ItemStack;set(Lnet/minecraft/core/component/DataComponentType;Ljava/lang/Object;)Ljava/lang/Object;"))
    private void polymerpatch$openClipboardItem(
            Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (player instanceof ServerPlayer owner && !PolymerNativeClients.isNative(owner))
            PolymerToolDialogs.openClipboardItem(owner, hand);
    }
}
