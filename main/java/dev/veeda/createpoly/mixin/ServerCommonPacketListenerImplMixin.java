package dev.veeda.createpoly.mixin;

import com.zurrtum.create.AllPackets;
import com.zurrtum.create.infrastructure.packet.s2c.PackagePortPlacementRequestPacket;
import com.zurrtum.create.infrastructure.packet.s2c.TrainPromptPacket;
import com.zurrtum.create.infrastructure.polymer.contraption.PolymerContraptionPassengers;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerBlueprintGui;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerDialogService;
import com.zurrtum.create.infrastructure.polymer.interaction.PolymerElevatorControls;
import com.zurrtum.create.infrastructure.polymer.interaction.PolymerPackagePorts;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import com.zurrtum.create.infrastructure.polymer.train.PolymerTrainHud;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerImplMixin {
    @Inject(method = "handlePong", at = @At("HEAD"), cancellable = true)
    private void acknowledgeElevatorSlot(ServerboundPongPacket packet, CallbackInfo ci) {
        if (NativePresentationPolicy.nativeReceiver(this)) return;
        if ((Object) this instanceof ServerGamePacketListenerImpl listener
                && (PolymerElevatorControls.onPong(listener, packet) || PolymerTrainHud.onPong(listener, packet)))
            ci.cancel();
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void suppressCreateClientboundPacket(Packet<?> packet, CallbackInfo ci) {
        if (NativePresentationPolicy.nativeReceiver(this)) return;
        Packet<?> translated = PolymerContraptionPassengers.rewrite(packet);
        if (translated != packet && (Object) this instanceof ServerGamePacketListenerImpl listener) {
            listener.send(translated);
            ci.cancel();
            return;
        }
        if (packet instanceof PackagePortPlacementRequestPacket request
                && (Object) this instanceof ServerGamePacketListenerImpl gameListener)
            PolymerPackagePorts.onPlacementRequest(gameListener.player, request.pos());
        if (trainHud(packet)) {
            ci.cancel();
            return;
        }
        if (AllPackets.isCreateClientbound(packet) || PolymerBlueprintGui.isSourceWindowPacket(packet)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void suppressCreateClientboundPacketWithListener(
            Packet<?> packet, ChannelFutureListener listener, CallbackInfo ci) {
        if (NativePresentationPolicy.nativeReceiver(this)) return;
        Packet<?> translated = PolymerContraptionPassengers.rewrite(packet);
        if (translated != packet && (Object) this instanceof ServerGamePacketListenerImpl gameListener) {
            gameListener.send(translated, listener);
            ci.cancel();
            return;
        }
        if (packet instanceof PackagePortPlacementRequestPacket request
                && (Object) this instanceof ServerGamePacketListenerImpl gameListener)
            PolymerPackagePorts.onPlacementRequest(gameListener.player, request.pos());
        if (trainHud(packet)) {
            ci.cancel();
            return;
        }
        if (AllPackets.isCreateClientbound(packet) || PolymerBlueprintGui.isSourceWindowPacket(packet)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "handleCustomClickAction",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/server/MinecraftServer;handleCustomClickAction(Lnet/minecraft/resources/Identifier;Ljava/util/Optional;)V"),
            cancellable = true)
    private void routeCreateDialogAction(ServerboundCustomClickActionPacket packet, CallbackInfo ci) {
        if (NativePresentationPolicy.nativeReceiver(this)) return;
        if ((Object) this instanceof ServerGamePacketListenerImpl listener
                && PolymerDialogService.handleCustomAction(listener, packet)) {
            ci.cancel();
        }
    }

    private boolean trainHud(Packet<?> packet) {
        PacketListener self = (PacketListener) (Object) this;
        if (packet instanceof TrainPromptPacket prompt) PolymerTrainHud.onPrompt(self, prompt.text(), prompt.shadow());
        return packet instanceof ClientboundSystemChatPacket chat
                && chat.overlay()
                && PolymerTrainHud.captureOverlay(self, chat.content());
    }
}
