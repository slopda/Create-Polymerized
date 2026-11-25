package com.zurrtum.create.infrastructure.polymer.contraption;

import com.zurrtum.create.Create;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public final class PolymerShiftPacer {
    public interface FrameClock {
        long[] next(long now, int slots);
    }

    public static volatile FrameClock clock;
    public static volatile Consumer<UUID> spacingObserver;

    public static long SPIN = 1_000_000L;

    private static final ReentrantLock LOCK = new ReentrantLock();
    private static final Condition WORK = LOCK.newCondition();
    private static final Map<UUID, Lane<?>> LANES = new HashMap<>();
    private static Thread thread;
    private static long lastAnchor = Long.MIN_VALUE;

    private PolymerShiftPacer() {}

    private record Contribution<T>(PolymerShiftQueue<T> queue, long token, boolean sticky, List<T> packets) {}

    private static final class Frame<T> {
        final List<List<Contribution<T>>> slots = new ArrayList<>();
        long[] due;
        int next;

        List<Contribution<T>> slot(int index) {
            while (slots.size() <= index) slots.add(new ArrayList<>());
            return slots.get(index);
        }
    }

    static final class Lane<T> {
        final Consumer<List<T>> send;
        final ArrayDeque<Frame<T>> frames = new ArrayDeque<>();
        final Object sendLock = new Object();

        Frame<T> open;

        private Lane(Consumer<List<T>> send) {
            this.send = send;
        }

        void add(PolymerShiftQueue<T> queue, long token, boolean sticky, int slot, List<T> packets) {
            LOCK.lock();
            try {
                if (open == null) open = new Frame<>();
                open.slot(slot).add(new Contribution<>(queue, token, sticky, packets));
            } finally {
                LOCK.unlock();
            }
        }
    }

    @SuppressWarnings("unchecked")
    static <T> Lane<T> lane(UUID player, Consumer<List<T>> send) {
        LOCK.lock();
        try {
            start();
            return (Lane<T>) LANES.computeIfAbsent(player, id -> new Lane<>(send));
        } finally {
            LOCK.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    static <T> boolean sendInOrder(UUID player, List<T> packets) {
        LOCK.lock();
        try {
            Lane<T> lane = (Lane<T>) LANES.get(player);
            if (lane == null) return false;
            lane.add(null, 0, true, 0, List.copyOf(packets));
            return true;
        } finally {
            LOCK.unlock();
        }
    }

    static void forget(UUID player) {
        Lane<?> lane;
        LOCK.lock();
        try {
            lane = LANES.remove(player);
        } finally {
            LOCK.unlock();
        }
        if (lane != null) flushSticky(lane);
    }

    private static <T> void flushSticky(Lane<T> lane) {
        List<T> bundle = new ArrayList<>();
        LOCK.lock();
        try {
            if (lane.open != null) {
                lane.open.due = new long[lane.open.slots.size()];
                lane.frames.addLast(lane.open);
                lane.open = null;
            }
            for (Frame<T> frame : lane.frames)
                for (int k = frame.next; k < frame.slots.size(); k++)
                    for (Contribution<T> part : frame.slots.get(k)) if (part.sticky) bundle.addAll(part.packets);
            lane.frames.clear();
        } finally {
            LOCK.unlock();
        }
        if (!bundle.isEmpty())
            synchronized (lane.sendLock) {
                lane.send.accept(List.copyOf(bundle));
            }
    }

    static void commit(int slots) {
        long now = System.nanoTime();
        LOCK.lock();
        try {
            FrameClock experiment = clock;
            long[] frame = experiment == null ? null : experiment.next(now, slots);
            long anchor = frame == null ? now : frame[0];
            long step = frame == null ? 16_000_000L : frame[1];
            boolean any = false;
            for (Lane<?> lane : LANES.values()) any |= stamp(lane, anchor, step);
            if (any) WORK.signalAll();
        } finally {
            LOCK.unlock();
        }
    }

    private static <T> boolean stamp(Lane<T> lane, long anchor, long step) {
        Frame<T> frame = lane.open;
        if (frame == null) return false;
        lane.open = null;
        frame.due = new long[frame.slots.size()];
        for (int k = 0; k < frame.due.length; k++) frame.due[k] = anchor + k * step;
        lane.frames.addLast(frame);
        return true;
    }

    private static void start() {
        if (thread != null) return;
        thread = new Thread(PolymerShiftPacer::run, "polymerpatch-shift-pacer");
        thread.setDaemon(true);
        thread.start();
    }

    private static void run() {
        while (true) {
            List<Runnable> sends = new ArrayList<>();
            long wait = Long.MAX_VALUE;
            LOCK.lock();
            try {
                long now = System.nanoTime();
                for (Lane<?> lane : LANES.values()) wait = Math.min(wait, collect(lane, now, sends));
                if (sends.isEmpty()) {
                    if (wait == Long.MAX_VALUE) WORK.awaitUninterruptibly();
                    else if (wait > SPIN) WORK.awaitNanos(wait - SPIN);
                }
            } catch (InterruptedException ignored) {
            } finally {
                LOCK.unlock();
            }
            if (sends.isEmpty() && wait != Long.MAX_VALUE && wait <= SPIN) {
                long until = System.nanoTime() + wait;
                while (System.nanoTime() < until) Thread.onSpinWait();
            }
            for (Runnable send : sends) {
                try {
                    send.run();
                } catch (Throwable t) {
                    Create.LOGGER.error("[PolymerPatch] Shift pacer send failed; continuing", t);
                }
            }
        }
    }

    static boolean idle(UUID player) {
        LOCK.lock();
        try {
            Lane<?> lane = LANES.get(player);
            return lane == null || lane.open == null && lane.frames.isEmpty();
        } finally {
            LOCK.unlock();
        }
    }

    private static <T> long collect(Lane<T> lane, long now, List<Runnable> sends) {
        while (!lane.frames.isEmpty()) {
            var frames = lane.frames.iterator();
            Frame<T> head = frames.next(), second = frames.hasNext() ? frames.next() : null;
            boolean due = head.due[head.next] <= now || second != null && second.due[0] <= now;
            if (!due) return head.due[head.next] - now;
            List<Contribution<T>> parts = List.copyOf(head.slot(head.next));
            if (!parts.isEmpty()) sends.add(() -> emit(lane, parts));
            if (++head.next >= head.due.length) lane.frames.removeFirst();
        }
        return Long.MAX_VALUE;
    }

    private static <T> void emit(Lane<T> lane, List<Contribution<T>> parts) {
        synchronized (lane.sendLock) {
            List<T> bundle = new ArrayList<>();
            for (Contribution<T> part : parts)
                if (part.sticky || part.queue.valid(part.token)) bundle.addAll(part.packets);
            if (!bundle.isEmpty()) lane.send.accept(List.copyOf(bundle));
        }
    }
}
