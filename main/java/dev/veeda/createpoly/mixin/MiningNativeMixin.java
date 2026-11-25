package dev.veeda.createpoly.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.zurrtum.create.infrastructure.polymer.block.PolymerPlacementAnimations;
import com.zurrtum.create.infrastructure.polymer.collision.PolymerProxyMining;
import com.zurrtum.create.infrastructure.polymer.effects.PolymerBlockParticles;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerPlayerGameMode.class, priority = 1100)
public abstract class MiningNativeMixin {
    @Shadow
    protected ServerLevel level;

    @Shadow
    @Final
    protected ServerPlayer player;

    @Shadow
    private boolean isDestroyingBlock;

    @Shadow
    private BlockPos destroyPos;

    @Shadow
    private boolean hasDelayedDestroy;

    @Shadow
    private BlockPos delayedDestroyPos;

    @WrapOperation(
            method = "useItem",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/item/ItemStack;use(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult polymerBucketSwing(
            ItemStack stack, Level world, Player player, InteractionHand hand, Operation<InteractionResult> original) {
        if (NativePresentationPolicy.nativeReceiver(player)) return original.call(stack, world, player, hand);
        Item item = stack.getItem();
        return PolymerPlacementAnimations.result(player, item, original.call(stack, world, player, hand));
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void copycatMiningParticles(CallbackInfo ci) {
        if (PolymerNativeClients.isNative(player)) return;
        PolymerBlockParticles.mining(
                player,
                level,
                isDestroyingBlock ? destroyPos : delayedDestroyPos,
                isDestroyingBlock || hasDelayedDestroy);
        if (isDestroyingBlock) PolymerProxyMining.progress(player, destroyPos);
        else if (hasDelayedDestroy) PolymerProxyMining.progress(player, delayedDestroyPos);
    }

    @Inject(method = "handleBlockBreakAction", at = @At("HEAD"))
    private void proxyAction(
            BlockPos pos,
            ServerboundPlayerActionPacket.Action action,
            Direction direction,
            int maxY,
            int sequence,
            CallbackInfo ci) {
        if (!PolymerNativeClients.isNative(player)) PolymerProxyMining.action(player, pos, action);
    }
}
