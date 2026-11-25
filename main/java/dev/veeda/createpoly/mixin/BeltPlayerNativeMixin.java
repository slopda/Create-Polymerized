package dev.veeda.createpoly.mixin;

import com.zurrtum.create.content.kinetics.belt.BeltBlockEntity;
import com.zurrtum.create.content.kinetics.belt.transport.BeltMovementHandler;
import com.zurrtum.create.content.kinetics.belt.transport.BeltMovementHandler.TransportedEntityInfo;
import com.zurrtum.create.infrastructure.polymer.contraption.PolymerBeltPlayers;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BeltMovementHandler.class)
public abstract class BeltPlayerNativeMixin {
    @Unique
    private static Vec3 polymerBeltStart;

    @Inject(method = "transportEntity", at = @At("HEAD"))
    private static void polymerBeltStart(
            BeltBlockEntity belt, Entity entity, TransportedEntityInfo info, CallbackInfo ci) {
        polymerBeltStart = entity instanceof ServerPlayer player && !PolymerNativeClients.isNative(player)
                ? entity.position()
                : null;
    }

    @Inject(method = "transportEntity", at = @At("TAIL"))
    private static void polymerBeltPush(
            BeltBlockEntity belt, Entity entity, TransportedEntityInfo info, CallbackInfo ci) {
        if (!(entity instanceof ServerPlayer player) || polymerBeltStart == null) return;
        Vec3 moved = player.position().subtract(polymerBeltStart);
        polymerBeltStart = null;
        if (moved.lengthSqr() < 1e-8) return;
        PolymerBeltPlayers.push(player, moved);
    }
}
