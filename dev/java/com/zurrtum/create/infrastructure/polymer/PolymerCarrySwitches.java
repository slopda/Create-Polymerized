package com.zurrtum.create.infrastructure.polymer;

import com.zurrtum.create.Create;
import com.zurrtum.create.infrastructure.polymer.contraption.PolymerShiftCarry;
import com.zurrtum.create.infrastructure.polymer.contraption.PolymerShiftPacer;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.minecraft.world.phys.Vec3;

public final class PolymerCarrySwitches implements ModInitializer {
    private static final int TRACE_TICKS = 20;

    @Override
    public void onInitialize() {
        if (Boolean.getBoolean("polymerpatch.carry.pacer")) PolymerShiftPacer.clock = new AdaptiveClock();
        if ("false".equalsIgnoreCase(System.getProperty("polymerpatch.carry.net"))) PolymerShiftCarry.net = false;
        if ("false".equalsIgnoreCase(System.getProperty("polymerpatch.carry.fineTrainCells")))
            PolymerShiftCarry.fineTrainCells = false;
        if ("false".equalsIgnoreCase(System.getProperty("polymerpatch.carry.pushAllCells")))
            PolymerShiftCarry.pushAllCells = false;
        if (Boolean.getBoolean("polymerpatch.carry.airMomentum")) PolymerShiftCarry.airMomentum = true;
        if (Boolean.getBoolean("polymerpatch.carry.trace")) PolymerShiftCarry.tracer = new Recorder();
        if (Boolean.getBoolean("polymerpatch.carry.pacerTrace"))
            PolymerShiftPacer.spacingObserver = PolymerCarrySwitches::observeSpacing;
    }

    private static final class AdaptiveClock implements PolymerShiftPacer.FrameClock {
        private static final long MAX_DELAY = Long.getLong("polymerpatch.carry.pacerMaxDelayMs", 30) * 1_000_000L;
        private static final long SLEW = 500_000L;
        private long lastAnchor = Long.MIN_VALUE, lastCommit, lastStep;
        private double period = 50e6;

        @Override
        public long[] next(long now, int slots) {
            if (lastAnchor != Long.MIN_VALUE) {
                long spacing = now - lastCommit;
                if (spacing > 0 && spacing < 250_000_000L) period += (Math.clamp(spacing, 40e6, 100e6) - period) * .05;
            }
            lastCommit = now;
            long step = (long) (period / slots);
            long target = lastAnchor == Long.MIN_VALUE ? now : lastAnchor + (long) period;
            long anchor = Math.min(target < now ? now : Math.max(now, target - SLEW), now + MAX_DELAY);
            if (lastAnchor != Long.MIN_VALUE)
                anchor = Math.max(anchor, lastAnchor + (slots - 1) * lastStep + step * 6 / 10);
            lastAnchor = anchor;
            lastStep = step;
            return new long[] {anchor, step};
        }
    }

    private static final Map<UUID, long[]> SPACING = new HashMap<>();

    private static synchronized void observeSpacing(UUID player) {
        long now = System.nanoTime();
        long[] s = SPACING.computeIfAbsent(player, id -> new long[6]);
        if (s[0] != 0) {
            long gap = now - s[0];
            s[1]++;
            s[gap < 12_000_000L ? 2 : gap <= 22_000_000L ? 3 : 4]++;
            s[5] = Math.max(s[5], gap);
            if (s[1] >= 600) {
                Create.LOGGER.info(
                        "[carry-pacer] {} sends={} <12ms={} 12-22ms={} >22ms={} max={}ms pacer={}",
                        player,
                        s[1],
                        s[2],
                        s[3],
                        s[4],
                        s[5] / 1_000_000L,
                        PolymerShiftPacer.clock != null);
                s[1] = s[2] = s[3] = s[4] = s[5] = 0;
            }
        }
        s[0] = now;
    }

    private static final class Recorder implements PolymerShiftCarry.Tracer {
        private static final class History {
            final ArrayDeque<String> lines = new ArrayDeque<>();
            long lastDump = -1000;
            boolean below;
        }

        private final Map<UUID, History> histories = new HashMap<>();

        @Override
        public void freeze(String who, int ticks, boolean wasCarrying, double lastGap, double y, boolean recovered) {
            Create.LOGGER.info(
                    "[carry-trace] FREEZE {} ticks={} wasCarrying={} lastGap={} y={} recovered={}",
                    who,
                    ticks,
                    wasCarrying,
                    number(lastGap),
                    String.format(Locale.ROOT, "%.3f", y),
                    recovered);
        }

        @Override
        public void tick(PolymerShiftCarry.TraceTick t) {
            History history = histories.computeIfAbsent(t.player(), id -> new History());
            Vec3 local = t.local(), push = t.push();
            String line = String.format(
                    Locale.ROOT,
                    "t=%d gap=%s cur=%s last=%s age=%d stale=%d sunk=%d aboard=%b carry=%b own=%b air=%b grd=%b elig=%b dy=%.3f v=%.3f tilt=%.1f lat=%d y=%.3f local=(%.2f,%.2f,%.2f) miss=%d rescue=%b trap=%b push=%s",
                    t.now(),
                    number(t.gap()),
                    number(t.currentGap()),
                    number(t.lastGap()),
                    t.age(),
                    t.staleTicks(),
                    t.sunkTicks(),
                    t.aboard(),
                    t.carrying(),
                    t.copied(),
                    t.airborne(),
                    t.grounded(),
                    t.eligible(),
                    t.dy(),
                    t.speed(),
                    t.tilt(),
                    t.latency(),
                    t.y(),
                    local.x,
                    local.y,
                    local.z,
                    t.misses(),
                    t.rescued(),
                    t.trapped(),
                    push == null ? "-" : String.format(Locale.ROOT, "(%.3f,%.3f,%.3f)", push.x, push.y, push.z));
            history.lines.addLast(line);
            while (history.lines.size() > TRACE_TICKS) history.lines.removeFirst();
            var log = Create.LOGGER;
            if (t.rescued()) log.info("[carry-trace] RESCUE {} {}", t.who(), line);
            if (push != null && push.lengthSqr() > .25) log.info("[carry-trace] PUSH {} {}", t.who(), line);
            if (t.sunk() && !t.rescued() && t.sunkTicks() == 1) log.info("[carry-trace] SUNK {} {}", t.who(), line);
            boolean lost = t.wasAboard() && !t.aboard() && t.eligible();
            boolean dropped = t.aboard() && !Double.isNaN(t.gap()) && t.gap() < -.6;
            if (!dropped) history.below = false;
            if ((lost || dropped && !history.below) && t.now() - history.lastDump >= 40) {
                history.lastDump = t.now();
                history.below = dropped;
                log.info(
                        "[carry-trace] {} {} last {} ticks:\n  {}",
                        lost ? "LOST" : "BELOW",
                        t.who(),
                        history.lines.size(),
                        String.join("\n  ", history.lines));
            }
        }

        private static String number(double value) {
            return Double.isNaN(value) ? "none" : String.format(Locale.ROOT, "%.3f", value);
        }
    }
}
