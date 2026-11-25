package dev.veeda.createpoly.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.zurrtum.create.content.logistics.stockTicker.StockTickerBlock;
import com.zurrtum.create.content.logistics.stockTicker.StockTickerBlockEntity;
import com.zurrtum.create.foundation.gui.menu.MenuProvider;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerStockCategoryGui;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = StockTickerBlock.class, remap = false)
abstract class StockTickerMenuHookMixin {
    @WrapOperation(
            method = "/^lambda\\$useItemOn\\$\\d+$/",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/zurrtum/create/foundation/gui/menu/MenuProvider;openHandledScreen(Lnet/minecraft/server/level/ServerPlayer;Lcom/zurrtum/create/foundation/gui/menu/MenuProvider;)V"))
    private static void polymerpatch$openStockTicker(
            ServerPlayer player,
            MenuProvider menu,
            Operation<Void> original,
            @Local(argsOnly = true) StockTickerBlockEntity ticker) {
        if (PolymerNativeClients.isNative(player)) original.call(player, menu);
        else PolymerStockCategoryGui.open(player, ticker, () -> {});
    }
}
