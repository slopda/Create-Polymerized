package com.zurrtum.create.infrastructure.polymer.contraption;

/**
 * Ownership/version contract for the existing Java carry output consumed by the Geyser patch.
 *
 * <p>Carry ids are -1 down to -0x40000000 (PolymerShiftCarry.nextId), so bit 30 is always set. The marker clears that
 * bit: marked ids are -0x40000001 down to Integer.MIN_VALUE, a range no other teleport id uses. The marker is
 * reversible, because the patch acknowledges the original id for the Bedrock client (see PolymerObserverFrameHistory).
 */
public final class PolymerBedrockCarryProtocol {
    public static final String READY_KEY = "polymerpatch.bedrockCarryProtocol";
    public static final int VERSION = 2;
    private static final int BIT = 0x40000000;

    private PolymerBedrockCarryProtocol() {}

    public static int mark(int sourceId) {
        if (sourceId >= 0) throw new IllegalArgumentException("Only owned negative carry ids can be marked");
        return sourceId & ~BIT;
    }

    public static boolean isMarked(int id) {
        return id < 0 && (id & BIT) == 0;
    }

    public static int unmark(int markedId) {
        if (!isMarked(markedId)) throw new IllegalArgumentException("Not a marked carry id");
        return markedId | BIT;
    }

    public static boolean ready() {
        return Integer.valueOf(VERSION).equals(System.getProperties().get(READY_KEY));
    }
}
