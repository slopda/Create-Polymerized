package dev.veeda.createpoly.devmixin;

import com.zurrtum.create.Create;
import dev.veeda.createpoly.devtrace.BreakTraceState;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
abstract class BreakEventTraceMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), require = 0)
    private void createpolyDev$traceBreak(Packet<?> packet, CallbackInfo ci) {
        if (!((Object) this instanceof ServerGamePacketListenerImpl self)) return;
        String who = self.player.getName().getString() + (self.player.isCreative() ? " (creative)" : " (survival)");
        if (packet instanceof ClientboundLevelEventPacket event
                && event.getType() == LevelEvent.PARTICLES_DESTROY_BLOCK) {
            Create.LOGGER.info(
                    "[break-trace] particles to {} at {} data {} = {}",
                    who,
                    event.getPos(),
                    event.getData(),
                    Block.stateById(event.getData()));
            StackTraceElement[] stack = new Throwable().getStackTrace();
            for (int i = 1; i < Math.min(stack.length, 14); i++) Create.LOGGER.info("[break-trace]   at {}", stack[i]);
        } else if (packet instanceof ClientboundBlockUpdatePacket update && BreakTraceState.near(update.getPos()))
            Create.LOGGER.info(
                    "[break-trace] block update to {} at {} = {}", who, update.getPos(), update.getBlockState());
        else if (packet instanceof ClientboundBlockDestructionPacket crack && BreakTraceState.near(crack.getPos()))
            Create.LOGGER.info("[break-trace] crack to {} at {} progress {}", who, crack.getPos(), crack.getProgress());
    }
}
