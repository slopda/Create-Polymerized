package com.zurrtum.create.infrastructure.polymer.contraption;

import java.util.ArrayDeque;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

final class PolymerShiftQueue<T> {
    private final ArrayDeque<List<T>> pending = new ArrayDeque<>();
    private final Consumer<List<T>> send;
    private final PolymerShiftPacer.Lane<T> lane;
    private long generation;
    private boolean closed;

    PolymerShiftQueue(Consumer<List<T>> send) {
        this(send, null);
    }

    PolymerShiftQueue(Consumer<List<T>> send, PolymerShiftPacer.Lane<T> lane) {
        this.send = send;
        this.lane = lane;
    }

    synchronized boolean closed() {
        return closed;
    }

    synchronized boolean valid(long token) {
        return !closed && token == generation;
    }

    synchronized void drain() {
        if (lane != null) return;
        generation++;
        if (!closed) while (!pending.isEmpty()) send.accept(pending.removeFirst());
    }

    void discard() {
        if (lane == null) {
            discardNow();
            return;
        }
        synchronized (lane.sendLock) {
            discardNow();
        }
    }

    void cancel() {
        if (lane == null) {
            cancelNow();
            return;
        }
        synchronized (lane.sendLock) {
            cancelNow();
        }
    }

    private synchronized void discardNow() {
        generation++;
        pending.clear();
    }

    private synchronized void cancelNow() {
        discardNow();
        closed = true;
    }

    void sendNow(List<T> packets) {
        long token;
        synchronized (this) {
            if (closed || packets.isEmpty()) return;
            if (lane == null) {
                send.accept(List.copyOf(packets));
                return;
            }
            token = generation;
        }
        lane.add(this, token, true, 0, List.copyOf(packets));
    }

    void submit(List<List<T>> batches, long spacing, BiConsumer<Runnable, Long> schedule) {
        long token;
        synchronized (this) {
            if (closed || batches.isEmpty()) return;
            if (lane == null) {
                schedule(batches, spacing, schedule);
                return;
            }
            token = generation;
        }
        for (int i = 0; i < batches.size(); i++) lane.add(this, token, false, i, List.copyOf(batches.get(i)));
    }

    private void schedule(List<List<T>> batches, long spacing, BiConsumer<Runnable, Long> schedule) {
        drain();
        send.accept(List.copyOf(batches.getFirst()));
        long token = ++generation;
        for (int i = 1; i < batches.size(); i++) pending.addLast(List.copyOf(batches.get(i)));
        for (int i = 1; i < batches.size(); i++)
            schedule.accept(
                    () -> {
                        synchronized (this) {
                            if (!closed && token == generation && !pending.isEmpty())
                                send.accept(pending.removeFirst());
                        }
                    },
                    i * spacing);
    }
}
