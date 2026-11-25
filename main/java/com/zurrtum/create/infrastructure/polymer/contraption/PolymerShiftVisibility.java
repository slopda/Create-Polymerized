package com.zurrtum.create.infrastructure.polymer.contraption;

final class PolymerShiftVisibility {
    static final int WARM_TICKS = 2;
    private final long createdTick;
    private boolean active;

    PolymerShiftVisibility(long tick) {
        createdTick = tick;
    }

    boolean active() {
        return active;
    }

    boolean activate(long tick) {
        if (active || tick - createdTick < WARM_TICKS) return false;
        active = true;
        return true;
    }
}
