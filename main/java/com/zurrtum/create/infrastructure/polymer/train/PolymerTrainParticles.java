package com.zurrtum.create.infrastructure.polymer.train;

import com.zurrtum.create.catnip.animation.LerpedFloat;
import com.zurrtum.create.catnip.math.VecHelper;
import com.zurrtum.create.content.trains.entity.Carriage;
import com.zurrtum.create.content.trains.entity.CarriageContraptionEntity;
import com.zurrtum.create.content.trains.entity.PolymerBogeyParticlePose;
import com.zurrtum.create.infrastructure.polymer.contraption.PolymerContraptionVisuals;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

public final class PolymerTrainParticles implements ModInitializer {
    private static final Map<CarriageContraptionEntity, State> STATES = new IdentityHashMap<>();

    static final class State {
        boolean arrived = true;
        int depressurise;
        double prevMotion;
        Vec3 previous;
        ServerLevel level;
        final Carriage carriage;
        final LerpedFloat brakes = LerpedFloat.linear();
        final RandomSource random = RandomSource.create();

        State(Carriage carriage) {
            this.carriage = carriage;
        }

        void step(double speed, boolean stalled, double steamRelease) {
            if (arrived && speed > .01f) arrived = false;
            arrived |= stalled;
            if (speed < .002f) {
                if (!arrived) {
                    arrived = true;
                    depressurise = (int) (20 * steamRelease / 10.0f);
                }
            } else depressurise = 0;
            if (depressurise > 0) depressurise--;
            brakes.chase(prevMotion > speed + speed / 512.0f ? 1 : 0, .25f, LerpedFloat.Chaser.exp(.625f));
            brakes.tickChaser();
            prevMotion = speed;
        }

        boolean spark() {
            return depressurise == 0 || depressurise > 10;
        }

        float cutoff() {
            float value = prevMotion < 1 / 8.0f ? 0 : 1 / 8.0f;
            return prevMotion > 1 / 6.0f ? Math.max(value, brakes.getValue() * 1.15f) : value;
        }
    }

    record Emission(Vec3 position, Vec3 velocity) {}

    @Override
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(PolymerTrainParticles::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> STATES.clear());
    }

    private static void tick(MinecraftServer server) {
        Map<CarriageContraptionEntity, List<ServerPlayer>> viewers = new IdentityHashMap<>();
        for (ServerPlayer player : NativePresentationPolicy.presentationPlayers(server.getPlayerList())) {
            if (PolymerNativeClients.isNative(player)) continue;
            for (var entity : PolymerContraptionVisuals.watchedContraptions(player))
                if (entity instanceof CarriageContraptionEntity carriage)
                    viewers.computeIfAbsent(carriage, key -> new ArrayList<>()).add(player);
        }
        STATES.keySet().removeIf(entity -> entity.isRemoved() || !viewers.containsKey(entity));
        for (var entry : viewers.entrySet()) emit(entry.getKey(), entry.getValue());
    }

    private static void emit(CarriageContraptionEntity entity, List<ServerPlayer> viewers) {
        if (!(entity.level() instanceof ServerLevel world)
                || world.tickRateManager().isEntityFrozen(entity)) return;
        Carriage carriage = entity.getCarriage();
        if (carriage == null || carriage.train == null) {
            STATES.remove(entity);
            return;
        }
        var dimensional = carriage.getDimensionalIfPresent(world.dimension());
        if (dimensional == null || !dimensional.pointsInitialised || dimensional.leadingAnchor() == null) {
            STATES.remove(entity);
            return;
        }
        Vec3 anchor = dimensional.leadingAnchor();
        viewers = viewers.stream()
                .filter(player -> anchor.closerThan(player.getCamera().position(), 64))
                .toList();
        if (viewers.isEmpty()) {
            STATES.remove(entity);
            return;
        }
        State state = STATES.get(entity);
        if (state == null || state.carriage != carriage || state.level != world) {
            STATES.put(entity, state = new State(carriage));
            state.level = world;
        }
        Vec3 position = entity.position(),
                motion = state.previous == null ? Vec3.ZERO : position.subtract(state.previous);
        state.previous = position;
        if (motion.lengthSqr() > 16 * 16 || !Double.isFinite(motion.lengthSqr())) {
            STATES.remove(entity);
            return;
        }
        state.step(motion.length(), entity.isStalled(), carriage.train.accumulatedSteamRelease);
        boolean spark = state.spark();
        float cutoff = state.cutoff();
        for (var bogey : carriage.bogeys) {
            if (bogey == null) continue;
            var angles = PolymerBogeyParticlePose.sample(bogey, entity);
            ParticleOptions particle =
                    vanilla(spark ? bogey.getStyle().contactParticle : bogey.getStyle().smokeParticle, spark);
            for (int j : new int[] {1, -1}) {
                if (state.random.nextFloat() > cutoff && (spark || state.random.nextInt(4) == 0)) continue;
                for (int i : new int[] {1, -1}) {
                    if (state.random.nextFloat() > cutoff && (spark || state.random.nextInt(4) == 0)) continue;
                    Emission emission = sample(
                            position,
                            motion,
                            entity.getViewYRot(1),
                            entity.getViewXRot(1),
                            angles.yaw(),
                            angles.pitch(),
                            bogey.isLeading,
                            carriage.bogeySpacing,
                            j,
                            i,
                            spark);
                    for (ServerPlayer viewer : viewers) send(world, viewer, particle, emission);
                }
            }
        }
    }

    static ParticleOptions vanilla(ParticleOptions options, boolean spark) {
        var id = options == null ? null : BuiltInRegistries.PARTICLE_TYPE.getKey(options.getType());
        return options instanceof SimpleParticleType
                        && id != null
                        && id.getNamespace().equals("minecraft")
                ? options
                : spark ? ParticleTypes.CRIT : ParticleTypes.POOF;
    }

    static void send(ServerLevel world, ServerPlayer player, ParticleOptions particle, Emission emission) {
        if (PolymerNativeClients.isNative(player) || player.level() != world) return;
        Vec3 v = emission.position, m = emission.velocity;
        world.sendParticles(player, particle, false, false, v.x, v.y, v.z, 0, m.x, m.y, m.z, 1);
    }

    static Emission sample(
            Vec3 position,
            Vec3 motion,
            float viewYaw,
            float viewPitch,
            float bogeyYaw,
            float bogeyPitch,
            boolean leading,
            int spacing,
            int j,
            int i,
            boolean spark) {
        Vec3 v = new Vec3(j * 1.15, spark ? -.6f : .32, i);
        Vec3 m = new Vec3(j * (spark ? .5 : .25), spark ? .49 : -.29, 0);
        m = VecHelper.rotate(VecHelper.rotate(m, bogeyPitch, Axis.X), bogeyYaw, Axis.Y);
        v = VecHelper.rotate(VecHelper.rotate(v, bogeyPitch, Axis.X), bogeyYaw, Axis.Y);
        v = VecHelper.rotate(v, -viewYaw - 90, Axis.Y);
        v = VecHelper.rotate(v, viewPitch, Axis.X);
        v = VecHelper.rotate(v, -180, Axis.Y);
        v = v.add(0, 0, leading ? 0 : -spacing);
        v = VecHelper.rotate(v, 180, Axis.Y);
        v = VecHelper.rotate(v, -viewPitch, Axis.X);
        v = VecHelper.rotate(v, viewYaw + 90, Axis.Y).add(position);
        return new Emission(v, m.add(motion.scale(.75f)));
    }
}
