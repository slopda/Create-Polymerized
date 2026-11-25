package dev.veeda.createpoly.mixin;

import com.zurrtum.create.infrastructure.polymer.ponder.PolymerPonderEntry;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PonderInventoryMixin {
    @Inject(
            method = "handleContainerClick",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;suppressRemoteUpdates()V"),
            cancellable = true)
    private void ponderInventory(ServerboundContainerClickPacket packet, CallbackInfo ci) {
        if (PolymerPonderEntry.inventorySwap(((ServerGamePacketListenerImpl) (Object) this).player, packet))
            ci.cancel();
    }
}
