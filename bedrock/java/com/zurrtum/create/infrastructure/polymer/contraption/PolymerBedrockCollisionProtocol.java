package com.zurrtum.create.infrastructure.polymer.contraption;

import java.util.UUID;

public final class PolymerBedrockCollisionProtocol {
    public static final String READY_KEY = "polymerpatch.bedrockCollisionProtocol";
    public static final int VERSION = 1;
    private static final long PREFIX = 0x7070636f6c6c0000L, MASK = 0xffffffffffff0000L;

    private PolymerBedrockCollisionProtocol() {}

    public static UUID mark(UUID original, double size) {
        float nativeSize = (float) size;
        validate(nativeSize);
        return new UUID(
                PREFIX | (original.getMostSignificantBits() & ~MASK),
                (original.getLeastSignificantBits() & 0xffffffff00000000L)
                        | Integer.toUnsignedLong(Float.floatToRawIntBits(nativeSize)));
    }

    public static boolean isMarked(UUID id) {
        return id != null && (id.getMostSignificantBits() & MASK) == PREFIX;
    }

    public static float size(UUID id) {
        if (!isMarked(id)) throw new IllegalArgumentException("Not an owned collision cell");
        float size = Float.intBitsToFloat((int) id.getLeastSignificantBits());
        validate(size);
        return size;
    }

    private static void validate(float size) {
        if (!Float.isFinite(size) || size <= 0 || size > 16)
            throw new IllegalArgumentException("Collision size must be finite and in (0,16]");
    }
}
