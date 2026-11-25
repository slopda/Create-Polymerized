package dev.veeda.createpoly.mixin;

import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class NativeAdapterPacketsMixin {
    @Shadow
    private Channel channel;

    private boolean nativeAdapterPacket(Packet<?> packet) {
        return NativePresentationPolicy.nativeReceiver(this)
                && NativePresentationPolicy.adapterPacket((Connection) (Object) this, packet);
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void suppressNativeAdapterPacket(Packet<?> packet, CallbackInfo ci) {
        if (nativeAdapterPacket(packet)) ci.cancel();
    }

    @Inject(
            method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void suppressNativeAdapterPacketWithListener(
            Packet<?> packet, ChannelFutureListener listener, CallbackInfo ci) throws Exception {
        if (nativeAdapterPacket(packet)) {
            if (listener != null) listener.operationComplete(channel.newSucceededFuture());
            ci.cancel();
        }
    }

    @Inject(
            method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
            at = @At("HEAD"),
            cancellable = true)
    private void suppressNativeAdapterPacketWithFlush(
            Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) throws Exception {
        if (nativeAdapterPacket(packet)) {
            if (listener != null) listener.operationComplete(channel.newSucceededFuture());
            ci.cancel();
        }
    }
}
