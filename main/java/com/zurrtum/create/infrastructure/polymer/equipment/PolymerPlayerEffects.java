package com.zurrtum.create.infrastructure.polymer.equipment;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.content.kinetics.turntable.TurntableBlockEntity;
import com.zurrtum.create.infrastructure.player.FakePlayerEntity;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class PolymerPlayerEffects {
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : NativePresentationPolicy.presentationPlayers(server.getPlayerList()))
                turntable(player);
        });
    }

    static void turntable(ServerPlayer player) {
        if (player instanceof FakePlayerEntity
                || player.isSpectator()
                || !player.onGround()
                || player.getAbilities().flying
                || player.level().tickRateManager().isFrozen()) return;
        BlockPos pos = player.blockPosition();
        if (!player.level().hasChunkAt(pos)
                || !player.level().getBlockState(pos).is(AllBlocks.TURNTABLE)
                || !(player.level().getBlockEntity(pos) instanceof TurntableBlockEntity be)) return;
        float angle = turntableAngle(be.getSpeed(), player.position().distanceTo(Vec3.atCenterOf(pos)));
        if (angle == 0) return;
        player.setYRot(player.getYRot() - angle);
        player.setYBodyRot(player.yBodyRot - angle);
        NativePresentationPolicy.sendPresentation(player, new ClientboundPlayerRotationPacket(-angle, true, 0, true));
    }

    static float turntableAngle(float speed, double distance) {
        float angle = speed / 3;
        if (distance > .25) angle *= (float) Mth.clamp((.5 - distance) * 2, 0, 1);
        return angle;
    }
}
