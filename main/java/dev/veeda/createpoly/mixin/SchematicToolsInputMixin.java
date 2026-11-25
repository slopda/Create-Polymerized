package dev.veeda.createpoly.mixin;

import com.zurrtum.create.infrastructure.polymer.hud.PolymerHudRadial;
import com.zurrtum.create.infrastructure.polymer.hud.PolymerRadialWrench;
import com.zurrtum.create.infrastructure.polymer.hud.PolymerToolbelt;
import com.zurrtum.create.infrastructure.polymer.schematic.PolymerQuill;
import com.zurrtum.create.infrastructure.polymer.schematic.PolymerSchematicTools;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class SchematicToolsInputMixin {
    @Inject(
            method = "handlePlayerAction",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/network/protocol/game/ServerboundPlayerActionPacket;getAction()Lnet/minecraft/network/protocol/game/ServerboundPlayerActionPacket$Action;"),
            cancellable = true)
    private void tools(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        var player = ((ServerGamePacketListenerImpl) (Object) this).player;
        if (packet.getAction() == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND
                && (PolymerHudRadial.swap(player)
                        || PolymerQuill.openTools(player)
                        || PolymerSchematicTools.cycle(player)
                        || PolymerRadialWrench.open(player)
                        || PolymerToolbelt.open(player))) ci.cancel();
    }

    @Inject(method = "handleAnimate", at = @At("TAIL"))
    private void leftClick(ServerboundSwingPacket packet, CallbackInfo ci) {
        var player = ((ServerGamePacketListenerImpl) (Object) this).player;
        if (packet.getHand() == InteractionHand.MAIN_HAND && !PolymerHudRadial.leftClick(player))
            PolymerSchematicTools.swing(player);
    }
}
