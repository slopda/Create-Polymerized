package dev.veeda.createpoly.mixin;

import com.zurrtum.create.AllSoundEvents;
import com.zurrtum.create.content.equipment.zapper.ShootableGadgetItemMethods;
import com.zurrtum.create.infrastructure.packet.s2c.PotatoCannonPacket;
import com.zurrtum.create.infrastructure.packet.s2c.ZapperBeamPacket;
import com.zurrtum.create.infrastructure.polymer.effects.PolymerVanillaEffects;
import java.util.function.Function;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ShootableGadgetItemMethods.class)
public abstract class GadgetEffectsVanillaMixin {
    @Inject(method = "sendPackets", at = @At("HEAD"))
    private static void polymerVanillaGadget(
            Player player, Function<Boolean, Packet<ClientGamePacketListener>> factory, CallbackInfo ci) {
        if (!(player.level() instanceof ServerLevel level)) return;
        Packet<ClientGamePacketListener> packet = factory.apply(false);
        if (packet instanceof PotatoCannonPacket shot) {
            PolymerVanillaEffects.sound(
                    level, shot.location(), AllSoundEvents.FWOOMP.getMainEvent(), SoundSource.PLAYERS, 1, shot.pitch());
            if (!shot.item().isEmpty())
                PolymerVanillaEffects.particles(
                        level,
                        new ItemParticleOption(ParticleTypes.ITEM, ItemStackTemplate.fromNonEmptyStack(shot.item())),
                        shot.location(),
                        2,
                        .025,
                        .025,
                        .025,
                        .05);
            PolymerVanillaEffects.particles(level, ParticleTypes.POOF, shot.location(), 2, .1, .1, .1, .05);
        } else if (packet instanceof ZapperBeamPacket beam) {
            PolymerVanillaEffects.sound(
                    level,
                    beam.location(),
                    AllSoundEvents.WORLDSHAPER_PLACE.getMainEvent(),
                    SoundSource.PLAYERS,
                    .1f,
                    .1f);
            PolymerVanillaEffects.particles(level, ParticleTypes.END_ROD, beam.target(), 10, .1, .1, .1, .1);
            PolymerVanillaEffects.particles(level, ParticleTypes.FIREWORK, beam.target(), 10, .1, .1, .1, 0);
            Vec3 step = beam.target().subtract(beam.location());
            int points = (int) Math.min(48, step.length() * 2);
            for (int i = 1; i < points; i++)
                PolymerVanillaEffects.particles(
                        level,
                        ParticleTypes.END_ROD,
                        beam.location().add(step.scale(i / (double) points)),
                        1,
                        0,
                        0,
                        0,
                        0);
        }
    }
}
