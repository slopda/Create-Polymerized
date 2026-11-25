package dev.veeda.createpoly.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.zurrtum.create.content.equipment.blueprint.BlueprintEntity;
import com.zurrtum.create.foundation.gui.menu.MenuProvider;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerBlueprintGui;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = BlueprintEntity.class, remap = false)
abstract class BlueprintMenuHookMixin {
    @WrapOperation(
            method = "interact",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/zurrtum/create/foundation/gui/menu/MenuProvider;openHandledScreen(Lnet/minecraft/server/level/ServerPlayer;Lcom/zurrtum/create/foundation/gui/menu/MenuProvider;)V"))
    private void polymerpatch$openBlueprint(ServerPlayer player, MenuProvider section, Operation<Void> original) {
        if (PolymerNativeClients.isNative(player)) original.call(player, section);
        else
            PolymerBlueprintGui.open(
                    player, (BlueprintEntity) (Object) this, ((BlueprintEntity.BlueprintSection) section).index);
    }
}
