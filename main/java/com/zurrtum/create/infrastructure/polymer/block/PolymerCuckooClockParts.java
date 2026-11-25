package com.zurrtum.create.infrastructure.polymer.block;

import static com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.model;
import static com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.part;
import static com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.progress;
import static com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.rotateToFace;
import static com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.rotationOffset;
import static com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.spin;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.catnip.animation.LerpedFloat;
import com.zurrtum.create.catnip.math.AngleHelper;
import com.zurrtum.create.catnip.math.VecHelper;
import com.zurrtum.create.content.kinetics.clock.CuckooClockBlock;
import com.zurrtum.create.content.kinetics.clock.CuckooClockBlockEntity;
import com.zurrtum.create.content.kinetics.clock.CuckooClockBlockEntity.Animation;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.entity.animal.pig.PigSoundVariant;
import net.minecraft.world.entity.animal.pig.PigSoundVariants;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.timeline.Timelines;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

final class PolymerCuckooClockParts {
    private static final Identifier SHAFT_HALF = model("shaft_half");
    private static final Identifier HOUR_HAND = model("cuckoo_clock/hour_hand");
    private static final Identifier MINUTE_HAND = model("cuckoo_clock/minute_hand");
    private static final Identifier LEFT_DOOR = model("cuckoo_clock/left_door");
    private static final Identifier RIGHT_DOOR = model("cuckoo_clock/right_door");
    private static final Identifier PIG = model("cuckoo_clock/pig");
    private static final Identifier CREEPER = model("cuckoo_clock/creeper");

    private static final Map<CuckooClockBlockEntity, State> STATES = new WeakHashMap<>();

    private static final class State {
        final LerpedFloat hourHand = LerpedFloat.angular();
        final LerpedFloat minuteHand = LerpedFloat.angular();
        long lastTick = Long.MIN_VALUE;
    }

    private PolymerCuckooClockParts() {}

    static void register() {
        PolymerBlockParts.<CuckooClockBlockEntity>register(
                PolymerCuckooClockParts::clock, AllBlocks.CUCKOO_CLOCK, AllBlocks.MYSTERIOUS_CUCKOO_CLOCK);
    }

    private static void clock(
            CuckooClockBlockEntity be, BlockState state, float time, List<PolymerBlockParts.Part> out) {
        Direction facing = state.getValue(CuckooClockBlock.HORIZONTAL_FACING);
        State clock;
        synchronized (STATES) {
            clock = STATES.computeIfAbsent(be, k -> new State());
        }
        Level level = be.getLevel();
        long tick = level == null ? 0 : level.getGameTime();
        boolean advance = tick != clock.lastTick;
        if (advance) {
            clock.lastTick = tick;
            if (be.getSpeed() != 0 && level != null) {
                moveHands(clock, level);
                if (level instanceof ServerLevel server) {
                    effects(be, server, tick);
                }
            }
        }

        Direction.Axis axis = facing.getAxis();
        float angle = progress(be.getSpeed(), time) + rotationOffset(state, axis, be.getBlockPos());
        out.add(part(SHAFT_HALF, spin(axis, angle).mul(rotateToFace(facing.getOpposite())))
                .identified("cuckoo:shaft"));

        Matrix4f up = spin(Direction.UP, AngleHelper.horizontalAngle(facing.getCounterClockWise()));
        out.add(part(
                        HOUR_HAND,
                        new Matrix4f(up)
                                .mul(PolymerBlockParts.pivot(east(clock.hourHand.getValue()), 0.125f, 0.375f, 0.5f)))
                .identified("cuckoo:hour"));
        out.add(part(
                        MINUTE_HAND,
                        new Matrix4f(up)
                                .mul(PolymerBlockParts.pivot(east(clock.minuteHand.getValue()), 0.125f, 0.375f, 0.5f)))
                .identified("cuckoo:minute"));

        float doorAngle = doorAngle(be);
        Quaternionf right = new Quaternionf().setAngleAxis(Mth.DEG_TO_RAD * doorAngle, 0, 1, 0);
        Quaternionf left = new Quaternionf(right).conjugate();
        out.add(part(LEFT_DOOR, new Matrix4f(up).mul(PolymerBlockParts.pivot(left, 0.125f, 0, 0.375f)))
                .identified("cuckoo:left_door"));
        out.add(part(RIGHT_DOOR, new Matrix4f(up).mul(PolymerBlockParts.pivot(right, 0.125f, 0, 0.625f)))
                .identified("cuckoo:right_door"));

        if (doorAngle != 0 && be.animationType != null && be.animationType != Animation.NONE) {
            float offset = -(doorAngle / 135) * 0.5f + 0.625f;
            if (offset <= 0.4f) {
                Identifier figure = be.animationType == Animation.PIG ? PIG : CREEPER;
                out.add(part(figure, new Matrix4f(up).translate(offset, 0, 0)).identified("cuckoo:figure"));
            }
        }
    }

    private static void moveHands(State clock, Level world) {
        RegistryAccess registryAccess = world.registryAccess();
        int dayTime = world.dimensionType()
                .defaultClock()
                .or(() -> registryAccess.get(WorldClocks.OVERWORLD))
                .map(c -> (int) (world.clockManager().getInstance(c).totalTicks()
                        % registryAccess
                                .get(Timelines.OVERWORLD_DAY)
                                .flatMap(timeline -> timeline.value().periodTicks())
                                .orElse(24000)))
                .orElse(0);
        int hours = (dayTime / 1000 + 6) % 24;
        int minutes = dayTime % 1000 * 60 / 1000;
        clock.hourHand.chase(360 / 12 * (hours % 12), 0.2f, LerpedFloat.Chaser.EXP);
        clock.minuteHand.chase(360 / 60 * minutes, 0.2f, LerpedFloat.Chaser.EXP);
        clock.hourHand.tickChaser();
        clock.minuteHand.tickChaser();
    }

    static float doorAngle(CuckooClockBlockEntity be) {
        if (be.animationType == null || be.animationType == Animation.NONE) {
            return 0;
        }
        float value = be.animationProgress.getValue();
        if (value < 25 || value >= 70) {
            return 0;
        }
        int step, minValue;
        if (be.animationType != Animation.SURPRISE) {
            minValue = 25;
            step = 5;
        } else if (value >= 29 && value < 62) {
            minValue = 29;
            step = 1;
        } else {
            return 0;
        }
        float local = value - (int) value - step + ((int) value - minValue) % (step * 3);
        if (local < 0) {
            return Mth.lerp((local + 5) / 5.0f, 0, 135);
        }
        if (local < step) {
            return 135;
        }
        return Mth.lerp((local - 5) / 5.0f, 135, 0);
    }

    private static void effects(CuckooClockBlockEntity be, ServerLevel level, long tick) {
        Animation type = be.animationType;
        if (type == null || type == Animation.NONE) {
            if (tick % 32 == 0) {
                sound(level, be, SoundEvents.NOTE_BLOCK_HAT, 1 / 16.0f, 2.0f);
            } else if (tick % 16 == 0) {
                sound(level, be, SoundEvents.NOTE_BLOCK_HAT, 1 / 16.0f, 1.5f);
            }
            return;
        }
        boolean surprise = type == Animation.SURPRISE;
        float value = be.animationProgress.getValue() - 1;
        if (value == 1) {
            sound(level, be, SoundEvents.NOTE_BLOCK_CHIME, 2, 0.5f);
        }
        if (value == 21) {
            sound(level, be, SoundEvents.NOTE_BLOCK_CHIME, 2, 0.793701f);
        }
        if (value > 30 && surprise) {
            Vec3 pos = VecHelper.offsetRandomly(VecHelper.getCenterOf(be.getBlockPos()), level.getRandom(), 0.5f);
            for (ServerPlayer player : listeners(level, be, 64)) {
                level.sendParticles(
                        player, ParticleTypes.LARGE_SMOKE, false, false, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
            }
        }
        if (value == 40 && surprise) {
            sound(level, be, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.TNT_PRIMED), 1.0f, 1.0f);
        }
        int step = surprise ? 3 : 15;
        for (int phase = 30; phase <= 60; phase += step) {
            if (value == phase - step / 3) {
                sound(level, be, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.CHEST_OPEN), 1 / 16.0f, 2.0f);
            }
            if (value == phase) {
                if (type == Animation.PIG) {
                    Registry<PigSoundVariant> variants =
                            level.registryAccess().lookupOrThrow(Registries.PIG_SOUND_VARIANT);
                    Holder.Reference<PigSoundVariant> holder = variants.get(PigSoundVariants.CLASSIC)
                            .or(variants::getAny)
                            .orElseThrow();
                    sound(level, be, holder.value().adultSounds().ambientSound(), 1 / 4.0f, 1.0f);
                } else {
                    sound(
                            level,
                            be,
                            BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.CREEPER_HURT),
                            1 / 4.0f,
                            3.0f);
                }
            }
            if (value == phase + step / 3) {
                sound(level, be, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.CHEST_CLOSE), 1 / 16.0f, 2.0f);
            }
        }
    }

    private static void sound(
            ServerLevel level, CuckooClockBlockEntity be, Holder<SoundEvent> sound, float volume, float pitch) {
        Vec3 at = VecHelper.getCenterOf(be.getBlockPos());
        double range = 16 * Math.max(1, volume);
        for (ServerPlayer player : listeners(level, be, range)) {
            NativePresentationPolicy.sendPresentation(
                    player,
                    new ClientboundSoundPacket(
                            sound,
                            SoundSource.BLOCKS,
                            at.x,
                            at.y,
                            at.z,
                            volume,
                            pitch,
                            level.getRandom().nextLong()));
        }
    }

    private static List<ServerPlayer> listeners(ServerLevel level, CuckooClockBlockEntity be, double range) {
        Vec3 at = VecHelper.getCenterOf(be.getBlockPos());
        return level.players().stream()
                .filter(player -> !PolymerNativeClients.isNative(player))
                .filter(player -> player.position().distanceToSqr(at) <= range * range)
                .toList();
    }

    private static Quaternionf east(float degrees) {
        return new Quaternionf().setAngleAxis(Mth.DEG_TO_RAD * degrees, 1, 0, 0);
    }
}
