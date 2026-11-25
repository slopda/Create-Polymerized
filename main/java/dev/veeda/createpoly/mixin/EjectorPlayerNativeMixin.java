package dev.veeda.createpoly.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.zurrtum.create.content.logistics.depot.EjectorBlockEntity;
import com.zurrtum.create.content.logistics.depot.EntityLauncher;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EjectorBlockEntity.class)
public abstract class EjectorPlayerNativeMixin {
    @Inject(
            method = "activateDeferred",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;isAlive()Z"))
    private void polymerLaunchVanillaPlayer(CallbackInfo ci, @Local Entity entity, @Local Direction facing) {
        if (!(entity instanceof ServerPlayer player) || !entity.isAlive()) return;
        if (PolymerNativeClients.isNative(player)) return;
        EjectorBlockEntity self = (EjectorBlockEntity) (Object) this;
        EntityLauncher launcher = self.launcher;
        entity.setOnGround(false);
        var pos = self.getBlockPos();
        entity.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
        launcher.applyMotion(entity, facing);
        player.hurtMarked = true;
    }
}
