package dev.veeda.createpoly.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientboundSetEntityDataPacket.class)
public abstract class NativeMetadataPacketMixin {
    @Shadow
    @Final
    private int id;

    @Unique
    private List<SynchedEntityData.DataValue<?>> nativeOriginalData;

    @Inject(method = "<init>(ILjava/util/List;)V", at = @At("RETURN"))
    private void preserveNativeData(int entityId, List<SynchedEntityData.DataValue<?>> values, CallbackInfo ci) {
        nativeOriginalData = List.copyOf(values);
    }

    @WrapMethod(method = "write")
    private void nativeMetadata(RegistryFriendlyByteBuf buffer, Operation<Void> original) {
        if (nativeOriginalData != null && PolymerNativeClients.isNative(PacketContext.get())) {
            buffer.writeVarInt(id);
            for (var value : nativeOriginalData) value.write(buffer);
            buffer.writeByte(255);
        } else original.call(buffer);
    }
}
