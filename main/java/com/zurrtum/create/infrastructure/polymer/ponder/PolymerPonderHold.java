package com.zurrtum.create.infrastructure.polymer.ponder;

final class PolymerPonderHold {
    static final long HOLD = 750_000_000L, FIRST_REPEAT = 1_500_000_000L, REPEAT_GAP = 250_000_000L;
    static final long TAP = 600_000_000L;

    private long first, last, repeating;
    private boolean started, repeated;

    boolean expired(long now) {
        return started && now - last > (repeated ? REPEAT_GAP : FIRST_REPEAT);
    }

    boolean press(long now) {
        if (!started || expired(now) || now < last) {
            started = true;
            repeated = false;
            first = last = now;
            return false;
        }
        if (now == last) return false;
        if (!repeated) {
            repeated = true;
            repeating = now;
        }
        last = now;
        return now - first >= HOLD && now - repeating >= 200_000_000L;
    }

    boolean appearsHeld(long now) {
        return started && !expired(now) && (repeated || now - first < TAP);
    }

    static float step(float value, boolean held) {
        return held ? Math.min(1, value + Math.max(.25f, value) * .25f * 11 / 15f) : Math.max(0, value - .05f);
    }
}
