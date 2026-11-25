package dev.veeda.createpoly.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientboundSetPassengersPacket.class)
public abstract class NativePassengersMixin {
    @Shadow
    @Final
    private int vehicle;

    @Unique
    private int[] nativeRealPassengers;

    @Inject(method = "<init>(Lnet/minecraft/world/entity/Entity;)V", at = @At("RETURN"))
    private void originalPassengers(Entity entity, CallbackInfo ci) {
        nativeRealPassengers =
                entity.getPassengers().stream().mapToInt(Entity::getId).toArray();
    }

    @WrapMethod(method = "write")
    private void nativePassengers(FriendlyByteBuf buffer, Operation<Void> original) {
        if (nativeRealPassengers != null && PolymerNativeClients.isNative(PacketContext.get())) {
            buffer.writeVarInt(vehicle);
            buffer.writeVarIntArray(nativeRealPassengers);
        } else original.call(buffer);
    }
}
