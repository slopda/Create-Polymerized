package com.zurrtum.create.infrastructure.polymer.effects;

import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

public final class PolymerVanillaEffects {
    private PolymerVanillaEffects() {}

    public static void sourceSound(
            ServerLevel level, BlockPos pos, SoundEvent sound, SoundSource source, float volume, float pitch) {
        double x = pos.getX() + .5, y = pos.getY() + .5, z = pos.getZ() + .5;
        double range = sound.getRange(volume);
        var packet = new ClientboundSoundPacket(
                BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound),
                source,
                x,
                y,
                z,
                volume,
                pitch,
                level.getRandom().nextLong());
        for (ServerPlayer viewer : level.players()) {
            if (NativePresentationPolicy.allowsPresentation(viewer) && viewer.distanceToSqr(x, y, z) < range * range)
                NativePresentationPolicy.sendPresentation(viewer, packet);
        }
    }

    public static int sourceParticles(
            ServerLevel level,
            ParticleOptions particle,
            double x,
            double y,
            double z,
            int count,
            double dx,
            double dy,
            double dz,
            double speed) {
        return sourceParticles(level, particle, false, false, x, y, z, count, dx, dy, dz, speed);
    }

    public static int sourceParticles(
            ServerLevel level,
            ParticleOptions particle,
            boolean force,
            boolean important,
            double x,
            double y,
            double z,
            int count,
            double dx,
            double dy,
            double dz,
            double speed) {
        int sent = 0;
        for (ServerPlayer viewer : level.players()) {
            if (NativePresentationPolicy.allowsPresentation(viewer)
                    && level.sendParticles(viewer, particle, force, important, x, y, z, count, dx, dy, dz, speed))
                sent++;
        }
        return sent;
    }

    public static void sound(
            ServerLevel level, Vec3 at, SoundEvent sound, SoundSource source, float volume, float pitch) {
        double range = 16 * Math.max(1, volume);
        for (ServerPlayer player : level.players()) {
            if (PolymerNativeClients.isNative(player) || player.position().distanceToSqr(at) > range * range) continue;
            NativePresentationPolicy.sendPresentation(
                    player,
                    new ClientboundSoundPacket(
                            Holder.direct(sound),
                            source,
                            at.x,
                            at.y,
                            at.z,
                            volume,
                            pitch,
                            level.getRandom().nextLong()));
        }
    }

    public static void particles(
            ServerLevel level,
            ParticleOptions particle,
            Vec3 at,
            int count,
            double dx,
            double dy,
            double dz,
            double speed) {
        for (ServerPlayer player : level.players()) {
            if (PolymerNativeClients.isNative(player) || player.position().distanceToSqr(at) > 64 * 64) continue;
            NativePresentationPolicy.sendPresentation(
                    player,
                    new ClientboundLevelParticlesPacket(
                            particle,
                            false,
                            false,
                            at.x,
                            at.y,
                            at.z,
                            (float) dx,
                            (float) dy,
                            (float) dz,
                            (float) speed,
                            count));
        }
    }
}
