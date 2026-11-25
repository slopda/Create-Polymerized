package dev.veeda.createpoly.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeRegistryIds;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.PacketEncoder;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PacketEncoder.class)
public abstract class NativePacketEncoderMixin {
    @WrapMethod(
            method =
                    "encode(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;Lio/netty/buffer/ByteBuf;)V")
    private void nativeCodecScope(
            ChannelHandlerContext context, Packet<?> packet, ByteBuf buffer, Operation<Void> original) {
        PolymerNativeRegistryIds.enterCodec();
        try {
            original.call(context, packet, buffer);
        } finally {
            PolymerNativeRegistryIds.exitCodec();
        }
    }
}
