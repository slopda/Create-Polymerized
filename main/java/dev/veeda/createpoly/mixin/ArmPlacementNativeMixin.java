package dev.veeda.createpoly.mixin;

import com.zurrtum.create.content.kinetics.mechanicalArm.ArmItem;
import com.zurrtum.create.content.logistics.depot.EjectorItem;
import com.zurrtum.create.infrastructure.polymer.interaction.PolymerArmTargets;
import com.zurrtum.create.infrastructure.polymer.interaction.PolymerEjectorTargets;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class ArmPlacementNativeMixin {
    @Inject(
            method =
                    "updateCustomBlockEntityTag(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/block/state/BlockState;)Z",
            at = @At("HEAD"))
    private void polymerArmPlacement(
            BlockPos pos,
            Level level,
            Player player,
            ItemStack stack,
            BlockState state,
            CallbackInfoReturnable<Boolean> cir) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) return;
        if (PolymerNativeClients.isNative(serverPlayer)) return;
        if ((Object) this instanceof ArmItem) PolymerArmTargets.onArmPlaced(serverPlayer, pos.immutable());
        if ((Object) this instanceof EjectorItem) PolymerEjectorTargets.onEjectorPlaced(serverPlayer, pos.immutable());
    }
}
