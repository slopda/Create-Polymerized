package dev.veeda.createpoly.mixin;

import com.zurrtum.create.content.logistics.box.PackageEntity;
import com.zurrtum.create.infrastructure.polymer.block.PolymerPackageVisuals;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PackageEntity.class, remap = false)
abstract class PackageEntityHooksMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void polymerpatch$packageVisuals(CallbackInfo ci) {
        PolymerPackageVisuals.tick((PackageEntity) (Object) this);
    }

    @Inject(method = "getAddEntityPacket", at = @At("HEAD"), cancellable = true)
    private void polymerpatch$packageSpawn(
            ServerEntity tracker, CallbackInfoReturnable<Packet<ClientGamePacketListener>> cir) {
        if (!NativePresentationPolicy.useOriginal(null))
            cir.setReturnValue(new ClientboundAddEntityPacket((PackageEntity) (Object) this, tracker));
    }
}
