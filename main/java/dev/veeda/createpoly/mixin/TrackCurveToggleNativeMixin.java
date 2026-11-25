package dev.veeda.createpoly.mixin;

import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import com.zurrtum.create.infrastructure.polymer.train.PolymerTrackCurveToggle;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class TrackCurveToggleNativeMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(
            method = "handlePlayerAction",
            cancellable = true,
            at =
                    @At(
                            value = "INVOKE",
                            shift = At.Shift.AFTER,
                            target =
                                    "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V"))
    private void polymerTrackCurveToggle(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        if (PolymerNativeClients.isNative(player)) return;
        if (packet.getAction() == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND
                && PolymerTrackCurveToggle.toggle(player)) ci.cancel();
    }
}
