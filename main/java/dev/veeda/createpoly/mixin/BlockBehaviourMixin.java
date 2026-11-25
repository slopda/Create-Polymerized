package dev.veeda.createpoly.mixin;

import com.zurrtum.create.content.equipment.clipboard.ClipboardBlock;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlockBehaviour.class, priority = 500)
public abstract class BlockBehaviourMixin {
    @Shadow
    @Final
    protected boolean hasCollision;

    private static boolean create$sourceShape(BlockState state) {
        var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id != null && id.getNamespace().equals("create");
    }

    @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
    private void create$originalCollision(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context,
            CallbackInfoReturnable<VoxelShape> cir) {
        if (!create$sourceShape(state)) return;
        if (state.getBlock() instanceof ClipboardBlock
                && context instanceof EntityCollisionContext entityContext
                && entityContext.getEntity() instanceof ServerPlayer player
                && !PolymerNativeClients.isNative(player)) {
            cir.setReturnValue(Shapes.empty());
            return;
        }
        cir.setReturnValue(hasCollision ? state.getShape(level, pos) : Shapes.empty());
    }

    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
    private void create$originalOutline(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context,
            CallbackInfoReturnable<VoxelShape> cir) {
        if (create$sourceShape(state)) cir.setReturnValue(Shapes.block());
    }
}
