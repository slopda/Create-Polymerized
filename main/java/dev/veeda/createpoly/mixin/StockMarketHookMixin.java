package dev.veeda.createpoly.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.zurrtum.create.content.logistics.packager.InventorySummary;
import com.zurrtum.create.content.logistics.stockTicker.StockTickerBlockEntity;
import com.zurrtum.create.content.logistics.stockTicker.StockTickerInteractionHandler;
import com.zurrtum.create.foundation.gui.menu.MenuProvider;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerStockMarketGui;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = StockTickerInteractionHandler.class, remap = false)
abstract class StockMarketHookMixin {
    @WrapOperation(
            method = "interactWithLogisticsManagerAt",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/zurrtum/create/foundation/gui/menu/MenuProvider;openHandledScreen(Lnet/minecraft/server/level/ServerPlayer;Lcom/zurrtum/create/foundation/gui/menu/MenuProvider;)V"))
    private static void polymerpatch$openMarket(
            ServerPlayer player, MenuProvider menu, Operation<Void> original, @Local StockTickerBlockEntity ticker) {
        if (PolymerNativeClients.isNative(player)) original.call(player, menu);
        else PolymerStockMarketGui.open(player, ticker);
    }

    @WrapOperation(
            method = "interactWithLogisticsManagerAt",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/zurrtum/create/content/logistics/packager/InventorySummary;divideAndSendTo(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/core/BlockPos;)V"))
    private static void polymerpatch$marketSummary(
            InventorySummary summary, ServerPlayer player, BlockPos pos, Operation<Void> original) {
        if (PolymerNativeClients.isNative(player)) original.call(summary, player, pos);
    }
}
