package com.zurrtum.create.infrastructure.polymer.gui;

import com.zurrtum.create.infrastructure.polymer.PolymerInventorySync;
import eu.pb4.sgui.api.containerwrappers.SlotBasedWrapperMenu;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public final class PolymerGuiLifecycle {
    private final int refreshInterval;
    private final boolean periodicSync;
    private int ticks;
    private boolean dirty, refreshDue, closed;

    public PolymerGuiLifecycle() {
        refreshInterval = 0;
        periodicSync = false;
    }

    public PolymerGuiLifecycle(int refreshInterval) {
        this(refreshInterval, true);
    }

    public PolymerGuiLifecycle(int refreshInterval, boolean periodicSync) {
        this(refreshInterval, periodicSync, false);
    }

    public PolymerGuiLifecycle(int refreshInterval, boolean periodicSync, boolean refreshOnFirstTick) {
        if (refreshInterval < 1) throw new IllegalArgumentException("Refresh interval must be positive");
        this.refreshInterval = refreshInterval;
        this.periodicSync = periodicSync;
        ticks = refreshOnFirstTick ? refreshInterval - 1 : 0;
    }

    public boolean isClosed() {
        return closed;
    }

    public void requestSync() {
        if (!closed) dirty = true;
    }

    public void tick(Runnable refresh, Runnable sync) {
        if (closed) return;
        if (refreshInterval > 0 && ++ticks == refreshInterval) {
            ticks = 0;
            dirty |= periodicSync;
            refreshDue = true;
        }
        if (refreshDue) {
            refresh.run();
            refreshDue = false;
        }
        if (closed) return;
        if (dirty) {
            dirty = false;
            try {
                sync.run();
            } catch (RuntimeException | Error failure) {
                dirty = true;
                throw failure;
            }
        }
    }

    public void close(Runnable commit, Runnable cleanup) {
        if (closed) return;
        closed = true;
        Throwable failure = null;
        try {
            commit.run();
        } catch (RuntimeException | Error error) {
            failure = error;
            throw error;
        } finally {
            if (failure == null) cleanup.run();
            else
                try {
                    cleanup.run();
                } catch (RuntimeException | Error cleanupError) {
                    if (cleanupError != failure) failure.addSuppressed(cleanupError);
                }
        }
    }

    public void tickWhileValid(boolean valid, Runnable close, @Nullable SlotBasedWrapperMenu menu) {
        if (!valid) {
            close.run();
            return;
        }
        if (menu != null) tick(() -> {}, menu::sendAllDataToRemote);
    }

    public void closeCarried(BooleanSupplier valid, AbstractContainerMenu source, ServerPlayer player) {
        close(
                () -> {
                    if (!valid.getAsBoolean()) return;
                    source.setCarried(ItemStack.EMPTY);
                    source.removed(player);
                },
                () -> PolymerInventorySync.afterMenu(player));
    }
}
