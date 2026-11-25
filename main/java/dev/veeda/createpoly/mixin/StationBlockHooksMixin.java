package dev.veeda.createpoly.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.zurrtum.create.AllItems;
import com.zurrtum.create.content.trains.station.StationBlock;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerStationForm;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = StationBlock.class, remap = false)
abstract class StationBlockHooksMixin {
    @Inject(
            method = "useItemOn",
            cancellable = true,
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/zurrtum/create/content/trains/station/StationBlock;onBlockEntityUse(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Ljava/util/function/Function;)Lnet/minecraft/world/InteractionResult;"))
    private void polymerpatch$useSchedule(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit,
            CallbackInfoReturnable<InteractionResult> cir) {
        if (!stack.is(AllItems.SCHEDULE)
                || !(player instanceof ServerPlayer owner)
                || PolymerNativeClients.isNative(owner)) return;
        cir.setReturnValue(((StationBlock) (Object) this)
                .onBlockEntityUseItemOn(
                        level, pos, station -> PolymerStationForm.useSchedule(station, stack, level, player, hand)));
    }

    @ModifyExpressionValue(
            method = "useItemOn",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;isClientSide()Z", ordinal = 0))
    private boolean polymerpatch$openStation(
            boolean client, @Local(argsOnly = true) BlockPos pos, @Local(argsOnly = true) Player player) {
        if (!client && player instanceof ServerPlayer owner && !PolymerNativeClients.isNative(owner))
            PolymerStationForm.openStationSettings(owner, pos);
        return client;
    }
}
