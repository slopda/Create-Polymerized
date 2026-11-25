package dev.veeda.createpoly.mixin;

import com.zurrtum.create.content.equipment.blueprint.BlueprintEntity;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlueprintEntity.class, remap = false)
abstract class BlueprintSpawnHookMixin {
    @Inject(method = "getAddEntityPacket", at = @At("HEAD"), cancellable = true)
    private void polymerpatch$blueprintSpawn(
            ServerEntity tracker, CallbackInfoReturnable<Packet<ClientGamePacketListener>> cir) {
        if (!NativePresentationPolicy.useOriginal(null))
            cir.setReturnValue(new ClientboundAddEntityPacket((BlueprintEntity) (Object) this, tracker));
    }
}
