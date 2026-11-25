package dev.veeda.createpoly.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.zurrtum.create.content.trains.schedule.ScheduleItem;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerStationForm;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ScheduleItem.class, remap = false)
abstract class ScheduleMenuHookMixin {
    @WrapOperation(
            method = "use",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/zurrtum/create/content/trains/schedule/ScheduleItem;openHandledScreen(Lnet/minecraft/server/level/ServerPlayer;)V"))
    private void polymerpatch$openSchedule(ScheduleItem item, ServerPlayer player, Operation<Void> original) {
        if (PolymerNativeClients.isNative(player)) original.call(item, player);
        else PolymerStationForm.openScheduleSettings(player);
    }
}
