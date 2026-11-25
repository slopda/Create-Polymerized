package dev.veeda.createpoly.mixin;

import com.zurrtum.create.infrastructure.polymer.contraption.PolymerObserverPlayers;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ObserverFramePacketsMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleAcceptTeleportPacket", at = @At("RETURN"))
    private void observeFrameAck(ServerboundAcceptTeleportationPacket packet, CallbackInfo ci) {
        PolymerObserverPlayers.acknowledgeFrame(player, packet.id());
    }

    @Inject(method = "handleMovePlayer", at = @At("RETURN"))
    private void observeAcceptedFramePosition(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        PolymerObserverPlayers.acceptFramePosition(player, packet);
    }
}
