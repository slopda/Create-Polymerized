package com.zurrtum.create.infrastructure.polymer.gui;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.LongSupplier;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.nbt.CompoundTag;

public final class PolymerDialogSessions<T> {
    private static final String TOKEN = "polymer_session";

    private record Entry<T>(String token, long opened, T value) {}

    private final Map<UUID, Entry<T>> entries = new ConcurrentHashMap<>();
    private final LongSupplier clock;
    private final long lifetime;
    private boolean bound;

    public static <T> Map<UUID, T> states() {
        return new StateMap<>();
    }

    @SafeVarargs
    public static void bindStates(Map<UUID, ?>... stores) {
        for (Map<UUID, ?> store : stores) {
            if (!(store instanceof StateMap<?> managed)) throw new IllegalArgumentException("Unmanaged dialog state");
            managed.bind();
        }
    }

    private static final class StateMap<T> extends ConcurrentHashMap<UUID, T> {
        private boolean bound;

        private synchronized void bind() {
            if (bound) return;
            bound = true;
            ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> remove(handler.player.getUUID()));
            ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear());
        }

        @Override
        public T put(UUID key, T value) {
            bind();
            return super.put(key, value);
        }

        @Override
        public T putIfAbsent(UUID key, T value) {
            bind();
            return super.putIfAbsent(key, value);
        }

        @Override
        public void putAll(Map<? extends UUID, ? extends T> values) {
            bind();
            super.putAll(values);
        }

        @Override
        public T computeIfAbsent(UUID key, Function<? super UUID, ? extends T> function) {
            bind();
            return super.computeIfAbsent(key, function);
        }

        @Override
        public T computeIfPresent(UUID key, BiFunction<? super UUID, ? super T, ? extends T> function) {
            bind();
            return super.computeIfPresent(key, function);
        }

        @Override
        public T compute(UUID key, BiFunction<? super UUID, ? super T, ? extends T> function) {
            bind();
            return super.compute(key, function);
        }

        @Override
        public T merge(UUID key, T value, BiFunction<? super T, ? super T, ? extends T> function) {
            bind();
            return super.merge(key, value, function);
        }
    }

    PolymerDialogSessions(Duration lifetime) {
        this(lifetime, System::nanoTime);
    }

    PolymerDialogSessions(Duration lifetime, LongSupplier clock) {
        this.clock = Objects.requireNonNull(clock);
        this.lifetime = lifetime.toNanos();
        if (this.lifetime <= 0 || this.lifetime > Long.MAX_VALUE / 2)
            throw new IllegalArgumentException("Invalid form lifetime");
    }

    synchronized void bindToServer() {
        if (bound) return;
        bound = true;
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 == 0) expire();
        });
    }

    CompoundTag open(UUID player, T value) {
        Objects.requireNonNull(player);
        Objects.requireNonNull(value);
        String token = UUID.randomUUID().toString();
        entries.put(player, new Entry<>(token, clock.getAsLong(), value));
        CompoundTag action = new CompoundTag();
        action.putString(TOKEN, token);
        return action;
    }

    Optional<T> consume(UUID player, CompoundTag payload) {
        Entry<T> entry = entries.get(player);
        if (entry == null) return Optional.empty();
        if (expired(entry)) {
            entries.remove(player, entry);
            return Optional.empty();
        }
        if (!entry.token().equals(payload.getString(TOKEN).orElse(null)) || !entries.remove(player, entry))
            return Optional.empty();
        return Optional.of(entry.value());
    }

    void forget(UUID player) {
        entries.remove(player);
    }

    void clear() {
        entries.clear();
    }

    void expire() {
        entries.entrySet().removeIf(entry -> expired(entry.getValue()));
    }

    private boolean expired(Entry<T> entry) {
        long elapsed = clock.getAsLong() - entry.opened();
        return elapsed < 0 || elapsed >= lifetime;
    }
}
