package dev.veeda.createpoly.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeRegistryIds;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import java.util.List;
import net.minecraft.network.PacketDecoder;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PacketDecoder.class)
public abstract class NativePacketDecoderMixin {
    @WrapMethod(method = "decode")
    private void nativeCodecScope(
            ChannelHandlerContext context, ByteBuf buffer, List<Object> output, Operation<Void> original) {
        PolymerNativeRegistryIds.enterCodec();
        try {
            original.call(context, buffer, output);
        } finally {
            PolymerNativeRegistryIds.exitCodec();
        }
    }
}
