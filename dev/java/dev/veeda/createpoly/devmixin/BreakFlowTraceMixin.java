package dev.veeda.createpoly.devmixin;

import com.zurrtum.create.Create;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
abstract class BreakFlowTraceMixin {
    @Shadow
    protected ServerLevel level;

    @Shadow
    @Final
    protected ServerPlayer player;

    private boolean createpolyDev$create(BlockPos pos) {
        return level.hasChunkAt(pos)
                && BuiltInRegistries.BLOCK
                        .getKey(level.getBlockState(pos).getBlock())
                        .getNamespace()
                        .equals(Create.MOD_ID);
    }

    @Inject(method = "handleBlockBreakAction", at = @At("HEAD"), require = 0)
    private void createpolyDev$action(
            BlockPos pos,
            ServerboundPlayerActionPacket.Action action,
            Direction direction,
            int maxY,
            int sequence,
            CallbackInfo ci) {
        if (createpolyDev$create(pos))
            Create.LOGGER.info(
                    "[break-trace] action {} at {} seq {} by {} ({})",
                    action,
                    pos,
                    sequence,
                    player.getName().getString(),
                    player.isCreative() ? "creative" : "survival");
    }

    @Inject(method = "destroyAndAck", at = @At("HEAD"), require = 0)
    private void createpolyDev$ack(BlockPos pos, int sequence, String reason, CallbackInfo ci) {
        if (createpolyDev$create(pos))
            Create.LOGGER.info("[break-trace] destroyAndAck {} seq {} reason '{}'", pos, sequence, reason);
    }

    @Inject(method = "destroyBlock", at = @At("HEAD"), require = 0)
    private void createpolyDev$destroyHead(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        dev.veeda.createpoly.devtrace.BreakTraceState.mark(pos);
        Create.LOGGER.info("[break-trace] destroyBlock start {} state {}", pos, level.getBlockState(pos));
    }

    @Inject(method = "destroyBlock", at = @At("RETURN"), require = 0)
    private void createpolyDev$destroyReturn(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        Create.LOGGER.info(
                "[break-trace] destroyBlock end {} -> {} now {}", pos, cir.getReturnValue(), level.getBlockState(pos));
    }
}
