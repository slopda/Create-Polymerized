package dev.veeda.createpoly.mixin;

import com.zurrtum.create.content.equipment.clipboard.ClipboardBlock;
import com.zurrtum.create.content.equipment.clipboard.ClipboardBlockEntity;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerToolDialogs;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ClipboardBlock.class, remap = false)
abstract class ClipboardBlockHookMixin {
    @Inject(method = "/^lambda\\$useWithoutItem\\$\\d+$/", at = @At("HEAD"))
    private static void polymerpatch$openClipboard(
            Level level,
            Player player,
            BlockPos pos,
            ClipboardBlockEntity clipboard,
            CallbackInfoReturnable<InteractionResult> cir) {
        if (player instanceof ServerPlayer owner && !PolymerNativeClients.isNative(owner))
            PolymerToolDialogs.openClipboardBlock(owner, pos, clipboard);
    }
}
