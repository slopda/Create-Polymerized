package dev.veeda.createpoly.mixin;

import com.zurrtum.create.content.kinetics.mechanicalArm.ArmBlock;
import com.zurrtum.create.content.logistics.depot.EjectorBlock;
import com.zurrtum.create.infrastructure.polymer.interaction.PolymerArmTargets;
import com.zurrtum.create.infrastructure.polymer.interaction.PolymerEjectorTargets;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ArmBlock.class, EjectorBlock.class})
public abstract class ArmPlacementNativeMixin {
    @Inject(
            method =
                    "setPlacedBy(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"))
    private void polymerArmPlacement(
            Level level, BlockPos pos, BlockState state, LivingEntity by, ItemStack stack, CallbackInfo ci) {
        if (level.isClientSide() || !(by instanceof ServerPlayer serverPlayer)) return;
        if (PolymerNativeClients.isNative(serverPlayer)) return;
        if ((Object) this instanceof ArmBlock) PolymerArmTargets.onArmPlaced(serverPlayer, pos.immutable());
        if ((Object) this instanceof EjectorBlock) PolymerEjectorTargets.onEjectorPlaced(serverPlayer, pos.immutable());
    }
}
