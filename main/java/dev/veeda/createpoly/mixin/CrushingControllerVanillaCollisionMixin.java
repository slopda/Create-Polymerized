package dev.veeda.createpoly.mixin;

import com.zurrtum.create.content.kinetics.crusher.CrushingWheelControllerBlock;
import com.zurrtum.create.content.kinetics.crusher.CrushingWheelControllerBlockEntity;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CrushingWheelControllerBlock.class)
public abstract class CrushingControllerVanillaCollisionMixin {
    @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
    private void polymerVanillaFallIn(
            BlockState state,
            BlockGetter world,
            BlockPos pos,
            CollisionContext context,
            CallbackInfoReturnable<VoxelShape> cir) {
        if (!state.getValue(CrushingWheelControllerBlock.VALID)) return;
        if (!(context instanceof EntityCollisionContext entityContext)
                || !(entityContext.getEntity() instanceof ServerPlayer player)
                || PolymerNativeClients.isNative(player)) return;
        if (player.isCreative() || player.isSpectator() || player.level().getDifficulty() == Difficulty.PEACEFUL)
            return;
        if (!(world.getBlockEntity(pos) instanceof CrushingWheelControllerBlockEntity be) || be.crushingspeed == 0)
            return;
        if (be.processingEntity == player || !be.isOccupied()) cir.setReturnValue(Shapes.empty());
    }
}
