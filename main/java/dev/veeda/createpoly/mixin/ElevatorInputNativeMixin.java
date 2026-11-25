package dev.veeda.createpoly.mixin;

import com.zurrtum.create.infrastructure.polymer.interaction.PolymerElevatorControls;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import com.zurrtum.create.infrastructure.polymer.train.PolymerTrainHud;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ElevatorInputNativeMixin {

    @Inject(
            method = "handleSetCarriedItem",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/server/level/ServerPlayer;getInventory()Lnet/minecraft/world/entity/player/Inventory;",
                            ordinal = 0),
            cancellable = true)
    private void selectElevatorFloor(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
        var player = ((ServerGamePacketListenerImpl) (Object) this).player;
        if (PolymerNativeClients.isNative(player)) return;
        if (PolymerTrainHud.onSlotChange(player, packet.getSlot())
                || PolymerElevatorControls.onSlotChange(player, packet.getSlot())) {
            ci.cancel();
        }
    }
}
