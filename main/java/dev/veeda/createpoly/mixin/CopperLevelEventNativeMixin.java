package dev.veeda.createpoly.mixin;

import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class CopperLevelEventNativeMixin {
    @Inject(
            method = "levelEvent(Lnet/minecraft/world/entity/Entity;ILnet/minecraft/core/BlockPos;I)V",
            at = @At("HEAD"))
    private void polymerCopperEvent(Entity source, int type, BlockPos pos, int data, CallbackInfo ci) {
        if (!(source instanceof ServerPlayer player) || type < 3003 || type > 3005) return;
        if (PolymerNativeClients.isNative(player)) return;
        ServerLevel level = (ServerLevel) (Object) this;
        boolean createBlock = "create"
                .equals(BuiltInRegistries.BLOCK
                        .getKey(level.getBlockState(pos).getBlock())
                        .getNamespace());
        boolean createTool = "create"
                        .equals(BuiltInRegistries.ITEM
                                .getKey(player.getMainHandItem().getItem())
                                .getNamespace())
                || "create"
                        .equals(BuiltInRegistries.ITEM
                                .getKey(player.getOffhandItem().getItem())
                                .getNamespace());
        if (!createBlock && !createTool) return;
        player.connection.send(new ClientboundLevelEventPacket(type, pos, data, false));
    }
}
