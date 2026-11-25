package dev.veeda.createpoly.mixin;

import com.zurrtum.create.content.contraptions.AbstractContraptionEntity;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AbstractContraptionEntity.class, remap = false)
abstract class ContraptionSpawnHookMixin {
    @Inject(method = "getAddEntityPacket", at = @At("HEAD"), cancellable = true)
    private void polymerpatch$contraptionSpawn(
            ServerEntity tracker, CallbackInfoReturnable<Packet<ClientGamePacketListener>> cir) {
        if (!NativePresentationPolicy.useOriginal(null))
            cir.setReturnValue(new ClientboundAddEntityPacket((AbstractContraptionEntity) (Object) this, tracker));
    }
}
