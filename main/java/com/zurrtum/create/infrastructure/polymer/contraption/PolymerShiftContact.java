package com.zurrtum.create.infrastructure.polymer.contraption;

final class PolymerShiftContact {
    private boolean airborne;
    private long jumpIntent = Long.MIN_VALUE;

    boolean airborne() {
        return airborne;
    }

    boolean update(
            long tick,
            boolean eligible,
            boolean jump,
            double gap,
            double currentGap,
            double relativeRise,
            double deckY) {
        if (!eligible) {
            airborne = false;
            jumpIntent = Long.MIN_VALUE;
            return false;
        }
        if (jump) jumpIntent = tick;
        if (!Double.isFinite(gap) || !Double.isFinite(relativeRise)) return false;
        boolean intent = jumpIntent != Long.MIN_VALUE && tick - jumpIntent >= 0 && tick - jumpIntent <= 3;
        double noise = Math.min(.12, Math.abs(deckY) / 3);
        if (!airborne) {
            if (currentGap > .06 && relativeRise > .16 + noise && intent
                    || gap > .22 && relativeRise > .12 + noise
                    || gap > .45 + .7 * Math.abs(deckY)) airborne = true;
        } else if (gap >= -.3 && gap <= .12 && relativeRise <= .08) {
            airborne = false;
        }
        return !airborne;
    }
}
