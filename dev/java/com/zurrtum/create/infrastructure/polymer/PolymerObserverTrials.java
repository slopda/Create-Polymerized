package com.zurrtum.create.infrastructure.polymer;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.zurrtum.create.infrastructure.polymer.contraption.PolymerObserverPlayers;
import com.zurrtum.create.infrastructure.polymer.contraption.PolymerShiftCarry;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class PolymerObserverTrials implements ModInitializer, PolymerObserverPlayers.Trial {
    private static final int DEFAULT_LEAD = PolymerObserverPlayers.DEFAULT_LEAD;
    private static final Map<UUID, Integer> LEADS = new HashMap<>();
    private static final Stats STATS = new Stats();
    private static final Map<UUID, Long> LOST_AT = new HashMap<>();
    private static final int BRIEF_LOSS_TICKS = 5;

    private static final class Stats {
        long since,
                supportedTicks,
                lostSupport,
                briefLoss,
                snapBacks,
                stalledSteps,
                firstPoseSteps,
                alignedSends,
                zeroForecasts;
        double snapSum, snapMax, forecastSum, forecastMax, stepSum;

        void reset(long tick) {
            since = tick;
            supportedTicks = lostSupport =
                    briefLoss = snapBacks = stalledSteps = firstPoseSteps = alignedSends = zeroForecasts = 0;
            snapSum = snapMax = forecastSum = forecastMax = stepSum = 0;
        }

        String describe(long tick) {
            return String.format(
                    Locale.ROOT,
                    "Observer stats over %d ticks: %d supported subject-ticks; support lost %d (%d regained within %d ticks); "
                            + "snap-backs %d (avg %.2f, max %.2f blocks behind live position); deck steps: avg %.3f blocks, "
                            + "%d stalled (pose repeated after a moving step), %d with no previous pose; aligned sends %d, "
                            + "forecast avg %.3f max %.3f blocks, %d zero while lead > 0.",
                    tick - since,
                    supportedTicks,
                    lostSupport,
                    briefLoss,
                    BRIEF_LOSS_TICKS,
                    snapBacks,
                    snapBacks == 0 ? 0 : snapSum / snapBacks,
                    snapMax,
                    supportedTicks == 0 ? 0 : stepSum / supportedTicks,
                    stalledSteps,
                    firstPoseSteps,
                    alignedSends,
                    alignedSends == 0 ? 0 : forecastSum / alignedSends,
                    forecastMax,
                    zeroForecasts);
        }
    }

    @Override
    public void onInitialize() {
        PolymerObserverPlayers.install(this);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LEADS.remove(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            LEADS.clear();
            LOST_AT.clear();
            STATS.reset(0);
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> {
            var observer = Commands.literal("observers");
            observer.then(Commands.literal("off").executes(context -> {
                setLead(context.getSource().getPlayerOrException(), -1);
                return 1;
            }));
            observer.then(Commands.literal("lead")
                    .then(Commands.argument("ticks", IntegerArgumentType.integer(0, 12))
                            .executes(context -> {
                                setLead(
                                        context.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(context, "ticks"));
                                return 1;
                            })));
            observer.then(Commands.literal("stats")
                    .executes(context -> {
                        long tick = context.getSource().getServer().overworld().getGameTime();
                        if (STATS.since == 0) STATS.since = tick;
                        context.getSource().sendSuccess(() -> Component.literal(STATS.describe(tick)), false);
                        return 1;
                    })
                    .then(Commands.literal("reset").executes(context -> {
                        STATS.reset(context.getSource().getServer().overworld().getGameTime());
                        context.getSource().sendSuccess(() -> Component.literal("Observer stats reset."), false);
                        return 1;
                    })));
            dispatcher.register(Commands.literal("createfly").then(observer));
        });
    }

    static void setLead(ServerPlayer viewer, int ticks) {
        PolymerObserverPlayers.clearViewer(viewer);
        if (PolymerNativeClients.isNative(viewer) || ticks == DEFAULT_LEAD) LEADS.remove(viewer.getUUID());
        else LEADS.put(viewer.getUUID(), Math.max(-1, ticks));
        viewer.sendSystemMessage(Component.literal(
                PolymerNativeClients.isNative(viewer)
                        ? "Native Create already handles observer alignment; the vanilla trial is disabled."
                        : ticks < 0
                                ? "Observer alignment off."
                                : "Observer alignment: " + ticks + " tick visual lead"
                                        + (ticks == DEFAULT_LEAD ? " (default)" : "")
                                        + ". Your movement is unchanged."));
    }

    @Override
    public int lead(ServerPlayer viewer) {
        return LEADS.getOrDefault(viewer.getUUID(), DEFAULT_LEAD);
    }

    @Override
    public void supported(
            ServerPlayer subject, PolymerShiftCarry.ObserverSupport support, PolymerShiftCarry.ObserverSupport prior) {
        STATS.supportedTicks++;
        Long lostAt = LOST_AT.remove(subject.getUUID());
        if (lostAt != null && subject.level().getGameTime() - lostAt <= BRIEF_LOSS_TICKS) STATS.briefLoss++;
        double step = support.current().origin().distanceTo(support.previous().origin());
        STATS.stepSum += step;
        if (support.previous() == support.current()) STATS.firstPoseSteps++;
        else if (step < 1e-6
                && prior != null
                && prior.entity() == support.entity()
                && prior.current().origin().distanceTo(prior.previous().origin()) >= 1e-6) STATS.stalledSteps++;
    }

    @Override
    public void lost(Set<UUID> subjects, long now) {
        for (UUID id : subjects) {
            STATS.lostSupport++;
            LOST_AT.put(id, now);
        }
        LOST_AT.values().removeIf(at -> now - at > BRIEF_LOSS_TICKS);
    }

    @Override
    public void snapBack(double snap) {
        STATS.snapBacks++;
        STATS.snapSum += snap;
        STATS.snapMax = Math.max(STATS.snapMax, snap);
    }

    @Override
    public void aligned(int lead, double ahead) {
        STATS.alignedSends++;
        STATS.forecastSum += ahead;
        STATS.forecastMax = Math.max(STATS.forecastMax, ahead);
        if (lead > 0 && ahead < 1e-6) STATS.zeroForecasts++;
    }
}
