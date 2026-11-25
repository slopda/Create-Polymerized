package dev.veeda.createpoly.mixin;

import com.zurrtum.create.infrastructure.polymer.contraption.PolymerObserverPlayers;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerCommonPacketListenerImpl.class, priority = 1100)
public abstract class ObserverPlayerPacketsMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void observerMove(Packet<?> packet, CallbackInfo ci) {
        if ((Object) this instanceof ServerGamePacketListenerImpl listener
                && PolymerObserverPlayers.suppress(listener.player, packet)) ci.cancel();
    }

    @Inject(
            method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void observerMoveWithListener(Packet<?> packet, ChannelFutureListener listener, CallbackInfo ci) {
        if ((Object) this instanceof ServerGamePacketListenerImpl game
                && PolymerObserverPlayers.suppress(game.player, packet)) ci.cancel();
    }
}
