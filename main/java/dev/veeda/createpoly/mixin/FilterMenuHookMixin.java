package dev.veeda.createpoly.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.zurrtum.create.content.logistics.filter.AttributeFilterItem;
import com.zurrtum.create.content.logistics.filter.FilterItem;
import com.zurrtum.create.content.logistics.filter.ListFilterItem;
import com.zurrtum.create.content.logistics.filter.PackageFilterItem;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerFilterForms;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = FilterItem.class, remap = false)
abstract class FilterMenuHookMixin {
    @WrapOperation(
            method = "use",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/zurrtum/create/content/logistics/filter/FilterItem;openHandledScreen(Lnet/minecraft/server/level/ServerPlayer;)V"))
    private void polymerpatch$openFilter(FilterItem self, ServerPlayer player, Operation<Void> original) {
        if (!PolymerNativeClients.isNative(player)) {
            if (self instanceof AttributeFilterItem && PolymerFilterForms.openAttributeFilterSettings(player)) return;
            if (self instanceof ListFilterItem && PolymerFilterForms.openListFilterSettings(player)) return;
            if (self instanceof PackageFilterItem && PolymerFilterForms.openPackageFilterSettings(player)) return;
        }
        original.call(self, player);
    }
}
