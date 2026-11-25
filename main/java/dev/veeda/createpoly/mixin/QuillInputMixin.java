package dev.veeda.createpoly.mixin;

import com.zurrtum.create.infrastructure.polymer.schematic.PolymerQuill;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerGamePacketListenerImpl.class, priority = 1100)
public abstract class QuillInputMixin {
    @Inject(
            method = "handleSetCarriedItem",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/server/level/ServerPlayer;getInventory()Lnet/minecraft/world/entity/player/Inventory;",
                            ordinal = 0),
            cancellable = true)
    private void quillScroll(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
        if (PolymerQuill.onSlotChange(((ServerGamePacketListenerImpl) (Object) this).player, packet.getSlot()))
            ci.cancel();
    }
}
